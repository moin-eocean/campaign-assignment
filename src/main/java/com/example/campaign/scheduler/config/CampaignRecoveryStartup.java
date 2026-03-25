package com.example.campaign.scheduler.config;

import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.enums.CampaignType;
import com.example.campaign.campaign.repository.CampaignRepository;
import com.example.campaign.scheduler.service.CampaignSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignRecoveryStartup implements ApplicationRunner {

    private final CampaignRepository campaignRepository;
    private final CampaignSchedulerService campaignSchedulerService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[Recovery] Checking for PENDING campaigns to re-register...");

        List<Campaign> pendingCampaigns = campaignRepository
                .findByTypeAndStatusAndScheduledAtAfter(CampaignType.SCHEDULED, CampaignStatus.SCHEDULED, LocalDateTime.now());

        for (Campaign campaign : pendingCampaigns) {
            try {
                if (campaignSchedulerService.isFireJobScheduled(campaign.getId())) {
                    log.info("[Recovery] Already scheduled in Quartz: {}", campaign.getId());
                    continue;
                }

                campaignSchedulerService.schedule(campaign.getId(), campaign.getScheduledAt());
                log.info("[Recovery] Re-registered campaignId={}", campaign.getId());

            } catch (Exception e) {
                log.error("[Recovery] Failed to re-register campaignId={}", campaign.getId(), e);
            }
        }

        log.info("[Recovery] Done. {} campaigns checked.", pendingCampaigns.size());
    }
}
