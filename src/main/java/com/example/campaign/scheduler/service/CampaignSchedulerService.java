package com.example.campaign.scheduler.service;

import com.example.campaign.scheduler.constant.SchedulerConstants;
import com.example.campaign.scheduler.job.CampaignDataPreloadJob;
import com.example.campaign.scheduler.job.CampaignFireJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Campaign ke liye dono Quartz jobs schedule/cancel karta hai.
 *
 * schedule()  → dono jobs register karo
 * cancel()    → dono jobs delete karo (campaign update/delete pe)
 * reschedule()→ naya time set karo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSchedulerService {

    private final Scheduler scheduler;

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Naya scheduled campaign register karo.
     * Dono jobs ek saath atomically schedule hoti hain.
     *
     * @param campaignId  campaign ka ID (String PK)
     * @param scheduledAt campaign fire hone ka exact time
     */
    public void schedule(Long campaignId, LocalDateTime scheduledAt) throws SchedulerException {

        log.info("[Scheduler] Scheduling campaignId={} at {}", campaignId, scheduledAt);

        LocalDateTime preloadAt = scheduledAt.minusMinutes(SchedulerConstants.PRELOAD_OFFSET_MINUTES);

        // Guard: agar preload time pehle hi nikal gaya toh adjust karo
        // (bahut late schedule kiya hua campaign — unlikely but safe)
        if (preloadAt.isBefore(LocalDateTime.now())) {
            log.warn("[Scheduler] Preload time already passed for campaignId={}, setting to now+10s", campaignId);
            preloadAt = LocalDateTime.now().plusSeconds(10);
        }

        JobDetail preloadJobDetail = buildPreloadJobDetail(campaignId);
        Trigger preloadTrigger = buildOneTimeTrigger(
                SchedulerConstants.PRELOAD_TRIG_PREFIX + campaignId,
                SchedulerConstants.GROUP_PRELOAD,
                preloadAt
        );

        JobDetail fireJobDetail = buildFireJobDetail(campaignId);
        Trigger fireTrigger = buildOneTimeTrigger(
                SchedulerConstants.FIRE_TRIG_PREFIX + campaignId,
                SchedulerConstants.GROUP_FIRE,
                scheduledAt
        );

        // Dono jobs atomically register karo
        scheduler.scheduleJob(preloadJobDetail, preloadTrigger);
        scheduler.scheduleJob(fireJobDetail, fireTrigger);

        log.info("[Scheduler] Registered PreloadJob at {} and FireJob at {} for campaignId={}",
                preloadAt, scheduledAt, campaignId);
    }

    /**
     * Campaign cancel ho gayi — dono jobs hata do.
     * Campaign update/delete pe call karo.
     *
     * @param campaignId campaign ID
     */
    public void cancel(Long campaignId) throws SchedulerException {
        log.info("[Scheduler] Cancelling jobs for campaignId={}", campaignId);

        deleteJobIfExists(
            new JobKey(SchedulerConstants.PRELOAD_JOB_PREFIX + campaignId, SchedulerConstants.GROUP_PRELOAD)
        );
        deleteJobIfExists(
            new JobKey(SchedulerConstants.FIRE_JOB_PREFIX + campaignId, SchedulerConstants.GROUP_FIRE)
        );

        log.info("[Scheduler] Jobs cancelled for campaignId={}", campaignId);
    }

    /**
     * Campaign ka time update ho gaya — cancel karo aur naya schedule karo.
     *
     * @param campaignId    campaign ID
     * @param newScheduledAt naya time
     */
    public void reschedule(Long campaignId, LocalDateTime newScheduledAt) throws SchedulerException {
        log.info("[Scheduler] Rescheduling campaignId={} to {}", campaignId, newScheduledAt);
        cancel(campaignId);
        schedule(campaignId, newScheduledAt);
    }

    /**
     * Check karo — campaign ka FireJob still pending hai ya nahi.
     */
    public boolean isFireJobScheduled(Long campaignId) throws SchedulerException {
        JobKey fireJobKey = new JobKey(
            SchedulerConstants.FIRE_JOB_PREFIX + campaignId,
            SchedulerConstants.GROUP_FIRE
        );
        return scheduler.checkExists(fireJobKey);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE BUILDERS
    // ─────────────────────────────────────────────────────────────────────

    private JobDetail buildPreloadJobDetail(Long campaignId) {
        return JobBuilder.newJob(CampaignDataPreloadJob.class)
                .withIdentity(SchedulerConstants.PRELOAD_JOB_PREFIX + campaignId, SchedulerConstants.GROUP_PRELOAD)
                .withDescription("Redis preload for campaignId=" + campaignId)
                .usingJobData(SchedulerConstants.KEY_CAMPAIGN_ID, campaignId)
                .storeDurably(false)   // Trigger ke saath hi delete ho
                .requestRecovery(true) // Crash ke baad recover kare
                .build();
    }

    private JobDetail buildFireJobDetail(Long campaignId) {
        return JobBuilder.newJob(CampaignFireJob.class)
                .withIdentity(SchedulerConstants.FIRE_JOB_PREFIX + campaignId, SchedulerConstants.GROUP_FIRE)
                .withDescription("RabbitMQ fire for campaignId=" + campaignId)
                .usingJobData(SchedulerConstants.KEY_CAMPAIGN_ID, campaignId)
                .storeDurably(false)
                .requestRecovery(true)
                .build();
    }

    /**
     * One-time trigger banata hai — ek baar fire karo, dobara nahi.
     * Misfire policy: FireNow — agar server down tha toh restart pe immediately fire karo.
     */
    private Trigger buildOneTimeTrigger(String triggerName, String triggerGroup, LocalDateTime fireAt) {
        Date fireDate = Date.from(fireAt.atZone(ZoneId.systemDefault()).toInstant());

        return TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup)
                .startAt(fireDate)
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionFireNow() // Crash recovery
                )
                .build();
    }

    private void deleteJobIfExists(JobKey jobKey) throws SchedulerException {
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("[Scheduler] Deleted job: {}", jobKey);
        } else {
            log.info("[Scheduler] Job not found (already fired or never existed): {}", jobKey);
        }
    }
}
