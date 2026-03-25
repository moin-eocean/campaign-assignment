package com.example.campaign.scheduler.job;

import com.example.campaign.common.service.CampaignRedisService;
import com.example.campaign.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JOB 1 — CampaignDataPreloadJob
 *
 * Campaign ke scheduledAt se 5 MINUTE PEHLE fire hota hai.
 * Kaam: DB se contacts + message fetch karo aur Redis mein daalo.
 *
 * @DisallowConcurrentExecution = ek campaign ke liye ek waqt mein
 *                                 sirf ek preload job chalegi.
 */
@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class CampaignDataPreloadJob implements Job {

    // Spring @Autowired kaam karta hai kyunki QuartzConfig mein SpringBeanJobFactory set ki hai
    @Autowired
    private CampaignRedisService campaignRedisService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        Long campaignId = context.getMergedJobDataMap()
                .getLong(Constants.KEY_CAMPAIGN_ID);

        log.info("[PreloadJob] Fired for campaignId={} — Loading data into Redis", campaignId);

        try {
            campaignRedisService.loadCampaignDataIntoRedis(campaignId);
            log.info("[PreloadJob] SUCCESS — campaignId={}", campaignId);

        } catch (Exception e) {
            log.error("[PreloadJob] FAILED for campaignId={} — Reason: {}", campaignId, e.getMessage(), e);
            // JobExecutionException throw karo taaki Quartz retry kar sake
            throw new JobExecutionException("Data preload failed for campaignId=" + campaignId, e, false);
        }
    }
}
