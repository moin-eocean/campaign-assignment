package com.example.campaign.scheduler.job;

import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.service.CampaignProducer;
import com.example.campaign.campaign.service.CampaignService;
import com.example.campaign.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
@DisallowConcurrentExecution
public class CampaignFireJob implements Job {

    @Autowired
    private CampaignService campaignService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        Long campaignId = context.getMergedJobDataMap()
                .getLong(Constants.KEY_CAMPAIGN_ID);

        log.info("[FireJob] Fired for campaignId={}", campaignId);

        try {
            campaignService.executeCampaign(campaignId);
            log.info("[FireJob] SUCCESS — Execution completed for campaignId={}", campaignId);

        } catch (Exception e) {
            log.error("[FireJob] FAILED for campaignId={} — Reason: {}", campaignId, e.getMessage(), e);
            throw new JobExecutionException("Campaign fire failed for campaignId=" + campaignId, e, false);
        }
    }
}
