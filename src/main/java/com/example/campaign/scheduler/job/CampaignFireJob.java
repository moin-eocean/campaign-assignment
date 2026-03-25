package com.example.campaign.scheduler.job;

import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.service.CampaignProducer;
import com.example.campaign.scheduler.constant.SchedulerConstants;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * JOB 2 — CampaignFireJob
 * <p>
 * Campaign ke exact scheduledAt pe fire hota hai.
 * Kaam:
 * 1. Redis check karo — data loaded hai ya nahi (safety guard)
 * 2. RabbitMQ mein campaignId publish karo
 * 3. Redis status → RUNNING
 *
 * @DisallowConcurrentExecution = ek campaign ek baar hi fire hogi.
 */
@Slf4j
@DisallowConcurrentExecution
public class CampaignFireJob implements Job {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CampaignProducer campaignProducer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        Long campaignId = context.getMergedJobDataMap()
                .getLong(SchedulerConstants.KEY_CAMPAIGN_ID);

        log.info("[FireJob] Fired for campaignId={}", campaignId);

        try {
            // ── Safety Guard ──────────────────────────────────────────────
            // Agar preload job kisi reason se fail ho gayi thi,
            // toh FireJob bhi fail kar do — incomplete state mein fire mat karo.
            String contactsKey = String.format(SchedulerConstants.REDIS_CONTACTS_KEY, campaignId);
            Long contactCount = redisTemplate.opsForList().size(contactsKey);

            if (contactCount == null || contactCount == 0) {
                log.error("[FireJob] ABORTED — No contacts in Redis for campaignId={}. " +
                        "Preload may have failed.", campaignId);
                throw new JobExecutionException(
                        "Redis contacts missing for campaignId=" + campaignId, false);
            }

            // ── Status Update ─────────────────────────────────────────────
            String statusKey = String.format(SchedulerConstants.REDIS_STATUS_KEY, campaignId);
            redisTemplate.opsForValue().set(statusKey, CampaignStatus.RUNNING.name());

            // ── RabbitMQ Publish ──────────────────────────────────────────
            campaignProducer.sendCampaign(campaignId);
            log.info("[FireJob] SUCCESS — campaignId={} published to RabbitMQ. " +
                    "Contacts in queue: {}", campaignId, contactCount);

        } catch (JobExecutionException e) {
            throw e; // Already wrapped, rethrow
        } catch (Exception e) {
            log.error("[FireJob] FAILED for campaignId={} — Reason: {}", campaignId, e.getMessage(), e);
            throw new JobExecutionException("Campaign fire failed for campaignId=" + campaignId, e, false);
        }
    }
}
