package com.example.campaign.scheduler.job;

import com.example.campaign.common.service.CampaignRedisService;
import com.example.campaign.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class CampaignDataPreloadJob implements Job {

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
            throw new JobExecutionException("Data preload failed for campaignId=" + campaignId, e, false);
        }
    }
}
