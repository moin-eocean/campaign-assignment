package com.example.campaign.campaign.executor;

import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.entity.MessageLog;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.repository.CampaignRepository;
import com.example.campaign.campaign.repository.MessageLogRepository;
import com.example.campaign.common.service.CampaignRedisService;
import com.example.campaign.whatsapp.SendResult;
import com.example.campaign.whatsapp.WhatsAppApiClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ContactExecutor {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = {500, 1000, 2000};
    private static final Set<String> NON_RETRYABLE_REASONS = Set.of("USER_BLOCKED", "INVALID_NUMBER");

    private final Long campaignId;
    private final CampaignRedisService campaignRedisService;
    private final WhatsAppApiClient whatsAppApiClient;
    private final Semaphore globalSemaphore;
    private final MessageLogRepository messageLogRepository;
    private final CampaignRepository campaignRepository;
    private final Campaign campaignRef;

    private AtomicReference<String> liveStatusRef;

    private final List<MessageLog> logBuffer = Collections.synchronizedList(new ArrayList<>());

    public ContactExecutor(
            Long campaignId,
            CampaignRedisService campaignRedisService,
            WhatsAppApiClient whatsAppApiClient,
            Semaphore globalSemaphore,
            MessageLogRepository messageLogRepository,
            CampaignRepository campaignRepository
    ) {
        this.campaignId = campaignId;
        this.campaignRedisService = campaignRedisService;
        this.whatsAppApiClient = whatsAppApiClient;
        this.globalSemaphore = globalSemaphore;
        this.messageLogRepository = messageLogRepository;
        this.campaignRepository = campaignRepository;
        this.campaignRef = campaignRepository.getReferenceById(campaignId);
    }

    public void execute() {
        log.info("[ContactExecutor] Starting execution for campaign: {}", campaignId);

        String messageJson = campaignRedisService.getCampaignMessage(campaignId);
        if (messageJson == null) {
            log.error("[ContactExecutor] No message found in Redis for campaign {}. Aborting.", campaignId);
            return;
        }

        liveStatusRef = new AtomicReference<>(CampaignStatus.RUNNING.name());

        try (ExecutorService vtPool = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("vt-campaign-" + campaignId + "-", 0).factory())) {

            Thread statusWatcherThread = Thread.ofPlatform()
                    .name("status-watcher-campaign-" + campaignId)
                    .start(new StatusWatcher(vtPool, campaignId, campaignRedisService, liveStatusRef));

            while (true) {
                String status = liveStatusRef.get();

                if (CampaignStatus.STOPPED.name().equals(status)) {
                    log.info("[ContactExecutor] Campaign {} STOPPED. Halting.", campaignId);
                    break;
                }

                if (CampaignStatus.PAUSED.name().equals(status)) {
                    log.debug("[ContactExecutor] Campaign {} PAUSED. Waiting 500ms...", campaignId);
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                try {
                    globalSemaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                String phoneNumber = campaignRedisService.lpopContact(campaignId);

                if (phoneNumber == null) {
                    log.info("[ContactExecutor] No more contacts for campaign {}.", campaignId);
                    globalSemaphore.release();
                    break;
                }

                final String phone = phoneNumber;
                vtPool.submit(() -> processContact(phone, messageJson));
            }

            vtPool.shutdown();
            try {
                if (!vtPool.awaitTermination(10, TimeUnit.MINUTES)) {
                    vtPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                vtPool.shutdownNow();
                Thread.currentThread().interrupt();
            }

            statusWatcherThread.interrupt();
            try {
                statusWatcherThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        }

        flushLogBuffer();

        String finalStatus = liveStatusRef.get();
        if (!CampaignStatus.STOPPED.name().equals(finalStatus)) {
            campaignRedisService.setCampaignStatus(campaignId, CampaignStatus.COMPLETED.name());
            campaignRepository.updateStatus(campaignId, CampaignStatus.COMPLETED.name());
            log.info("[ContactExecutor] Campaign {} marked as COMPLETED.", campaignId);
        }
    }

    /**
     * Ek VT — ek contact — ek message.
     * globalSemaphore ka release HAMESHA finally mein hoga.
     */
    private void processContact(String phoneNumber, String messageJson) {
        log.debug("[ContactExecutor] Processing: {} | campaign: {}", phoneNumber, campaignId);

        try {
            if (isStopped()) return;

            while (isPaused()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // finally mein release hoga
                }
                if (isStopped()) return;
            }

            if (isStopped()) return;

            SendResult result = null;
            int retryCount = 0;

            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                if (isStopped()) break;

                result = whatsAppApiClient.send(phoneNumber, messageJson);

                if (result.success()) break;

                boolean nonRetryable = NON_RETRYABLE_REASONS.contains(result.failureReason());
                boolean lastAttempt = (attempt == MAX_ATTEMPTS - 1);

                if (nonRetryable || lastAttempt) break;

                retryCount++;
                try {
                    TimeUnit.MILLISECONDS.sleep(RETRY_DELAYS_MS[attempt]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                log.debug("[ContactExecutor] Retry {}/{} for {} after {}ms",
                        attempt + 1, MAX_ATTEMPTS - 1, phoneNumber, RETRY_DELAYS_MS[attempt]);
            }

            if (isStopped() && result == null) return;

            // Redis stats update
            long now = System.currentTimeMillis() / 1000;
            if (result != null && result.success()) {
                campaignRedisService.incrementStat(campaignId, "sent");
                campaignRedisService.markContactSent(campaignId, phoneNumber, now);
            } else {
                String reason = result != null ? result.failureReason() : "UNKNOWN";
                campaignRedisService.incrementStat(campaignId, "failed");
                campaignRedisService.markContactFailed(campaignId, phoneNumber, reason + ":" + now);
            }

            logBuffer.add(buildMessageLog(phoneNumber, result, retryCount));

        } finally {
            globalSemaphore.release();
        }
    }

    private MessageLog buildMessageLog(String phoneNumber, SendResult result, int retryCount) {
        MessageLog entry = new MessageLog();
        entry.setCampaign(this.campaignRef);
        entry.setContactNumber(phoneNumber);
        entry.setContactName(null);
        entry.setRetryCount(retryCount);
        entry.setSentAt(LocalDateTime.now());

        if (result != null && result.success()) {
            entry.setStatus("SENT");
        } else {
            entry.setStatus("FAILED");
            entry.setFailureReason(result != null ? result.failureReason() : "UNKNOWN");
            entry.setRawError(result != null ? result.rawError() : "null result from API client");
        }
        return entry;
    }

    private void flushLogBuffer() {
        if (logBuffer.isEmpty()) return;
        try {
            log.info("[ContactExecutor] Flushing {} logs to MySQL for campaign {}", logBuffer.size(), campaignId);
            messageLogRepository.saveAll(new ArrayList<>(logBuffer));
            logBuffer.clear();
        } catch (Exception e) {
            log.error("[ContactExecutor] Flush failed for campaign {}: {}", campaignId, e.getMessage());
        }
    }

    private boolean isStopped() {
        return CampaignStatus.STOPPED.name().equals(liveStatusRef.get());
    }

    private boolean isPaused() {
        return CampaignStatus.PAUSED.name().equals(liveStatusRef.get());
    }


}