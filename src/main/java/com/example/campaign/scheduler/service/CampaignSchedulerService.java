package com.example.campaign.scheduler.service;

import com.example.campaign.common.constant.Constants;
import com.example.campaign.scheduler.job.CampaignDataPreloadJob;
import com.example.campaign.scheduler.job.CampaignFireJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSchedulerService {

    private final Scheduler scheduler;

    public void schedule(Long campaignId, LocalDateTime scheduledAt) throws SchedulerException {

        log.info("[Scheduler] Scheduling campaignId={} at {}", campaignId, scheduledAt);

        LocalDateTime preloadAt = scheduledAt.minusMinutes(Constants.PRELOAD_OFFSET_MINUTES);

        if (preloadAt.isBefore(LocalDateTime.now())) {
            log.warn("[Scheduler] Preload time already passed for campaignId={}, setting to now+10s", campaignId);
            preloadAt = LocalDateTime.now().plusSeconds(10);
        }

        JobDetail preloadJobDetail = buildPreloadJobDetail(campaignId);
        Trigger preloadTrigger = buildOneTimeTrigger(
                Constants.PRELOAD_TRIG_PREFIX + campaignId,
                Constants.GROUP_PRELOAD,
                preloadAt
        );

        JobDetail fireJobDetail = buildFireJobDetail(campaignId);
        Trigger fireTrigger = buildOneTimeTrigger(
                Constants.FIRE_TRIG_PREFIX + campaignId,
                Constants.GROUP_FIRE,
                scheduledAt
        );

        scheduler.scheduleJob(preloadJobDetail, preloadTrigger);
        scheduler.scheduleJob(fireJobDetail, fireTrigger);

        log.info("[Scheduler] Registered PreloadJob at {} and FireJob at {} for campaignId={}",
                preloadAt, scheduledAt, campaignId);
    }

    public void cancel(Long campaignId) throws SchedulerException {
        log.info("[Scheduler] Cancelling jobs for campaignId={}", campaignId);

        deleteJobIfExists(
            new JobKey(Constants.PRELOAD_JOB_PREFIX + campaignId, Constants.GROUP_PRELOAD)
        );
        deleteJobIfExists(
            new JobKey(Constants.FIRE_JOB_PREFIX + campaignId, Constants.GROUP_FIRE)
        );

        log.info("[Scheduler] Jobs cancelled for campaignId={}", campaignId);
    }

    public void reschedule(Long campaignId, LocalDateTime newScheduledAt) throws SchedulerException {
        log.info("[Scheduler] Rescheduling campaignId={} to {}", campaignId, newScheduledAt);
        cancel(campaignId);
        schedule(campaignId, newScheduledAt);
    }

    public boolean isFireJobScheduled(Long campaignId) throws SchedulerException {
        JobKey fireJobKey = new JobKey(
            Constants.FIRE_JOB_PREFIX + campaignId,
            Constants.GROUP_FIRE
        );
        return scheduler.checkExists(fireJobKey);
    }

    private JobDetail buildPreloadJobDetail(Long campaignId) {
        return JobBuilder.newJob(CampaignDataPreloadJob.class)
                .withIdentity(Constants.PRELOAD_JOB_PREFIX + campaignId, Constants.GROUP_PRELOAD)
                .withDescription("Redis preload for campaignId=" + campaignId)
                .usingJobData(Constants.KEY_CAMPAIGN_ID, campaignId)
                .storeDurably(false)   // Trigger ke saath hi delete ho
                .requestRecovery(true) // Crash ke baad recover kare
                .build();
    }

    private JobDetail buildFireJobDetail(Long campaignId) {
        return JobBuilder.newJob(CampaignFireJob.class)
                .withIdentity(Constants.FIRE_JOB_PREFIX + campaignId, Constants.GROUP_FIRE)
                .withDescription("RabbitMQ fire for campaignId=" + campaignId)
                .usingJobData(Constants.KEY_CAMPAIGN_ID, campaignId)
                .storeDurably(false)
                .requestRecovery(true)
                .build();
    }

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
