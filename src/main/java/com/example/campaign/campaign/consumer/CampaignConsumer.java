package com.example.campaign.campaign.consumer;

import com.example.campaign.campaign.executor.ContactExecutor;
import com.example.campaign.campaign.repository.CampaignRepository;
import com.example.campaign.campaign.repository.MessageLogRepository;
import com.example.campaign.common.service.CampaignRedisService;
import com.example.campaign.whatsapp.WhatsAppApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class CampaignConsumer {

    private final CampaignRedisService campaignRedisService;
    private final WhatsAppApiClient whatsAppApiClient;
    private final MessageLogRepository messageLogRepository;
    private final CampaignRepository campaignRepository;
    private final Semaphore globalSemaphore;
    private final ExecutorService campaignExecutorPool;
    private final Semaphore campaignSemaphore;

    public CampaignConsumer(
            CampaignRedisService campaignRedisService,
            WhatsAppApiClient whatsAppApiClient,
            MessageLogRepository messageLogRepository,
            CampaignRepository campaignRepository,
            @Qualifier("whatsappGlobalSemaphore") Semaphore globalSemaphore,
            @Qualifier("campaignExecutorPool") ExecutorService campaignExecutorPool,
            @Qualifier("campaignSemaphore") Semaphore campaignSemaphore) {
        this.campaignRedisService = campaignRedisService;
        this.whatsAppApiClient = whatsAppApiClient;
        this.messageLogRepository = messageLogRepository;
        this.campaignRepository = campaignRepository;
        this.globalSemaphore = globalSemaphore;
        this.campaignExecutorPool = campaignExecutorPool;
        this.campaignSemaphore = campaignSemaphore;
    }

    @RabbitListener(queues = "campaign.queue", concurrency = "1")
    public void onCampaignReceived(Long campaignId) {
        log.info("[CampaignConsumer] Received campaign: {}", campaignId);

        try {
            log.info("[CampaignConsumer] Waiting for campaign slot: {}", campaignId);
            campaignSemaphore.acquire();
            log.info("[CampaignConsumer] Slot acquired, submitting: {}", campaignId);

            ContactExecutor executor = new ContactExecutor(
                    campaignId,
                    campaignRedisService,
                    whatsAppApiClient,
                    globalSemaphore,
                    messageLogRepository,
                    campaignRepository,
                    campaignSemaphore);

            try {
                campaignExecutorPool.submit(() -> {
                    try {
                        executor.execute();
                    } catch (Exception e) {
                        log.error("[CampaignConsumer] Error executing campaign {}: {}",
                                campaignId, e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                campaignSemaphore.release();
                throw e;
            }
        } catch (InterruptedException e) {
            log.error("[CampaignConsumer] Interrupted while waiting for slot {}: {}",
                    campaignId, e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[CampaignConsumer] Unexpected error for campaign {}: {}",
                    campaignId, e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Unexpected error", e);
        }
    }
}
