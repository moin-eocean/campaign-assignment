package com.example.campaign.campaign.executor;

import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.common.service.CampaignRedisService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
class StatusWatcher implements Runnable {
    private final ExecutorService vtPool;
    private final Long campaignId;
    private final CampaignRedisService campaignRedisService;
    private final AtomicReference<String> liveStatusRef;

    StatusWatcher(ExecutorService vtPool, Long campaignId, CampaignRedisService campaignRedisService, AtomicReference<String> liveStatusRef) {
        this.vtPool = vtPool;
        this.campaignId = campaignId;
        this.campaignRedisService = campaignRedisService;
        this.liveStatusRef = liveStatusRef;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(500);

                String latest = campaignRedisService.getCampaignStatus(campaignId);
                String current = liveStatusRef.get();

                if (latest != null && !current.equals(latest)) {
                    log.info("[StatusWatcher] Campaign {} status: {} → {}", campaignId, current, latest);
                    liveStatusRef.set(latest);

                    if (CampaignStatus.STOPPED.name().equals(latest)) {
                        log.info("[StatusWatcher] shutdownNow() for campaign {}", campaignId);
                        vtPool.shutdownNow(); // parked VTs interrupt ho jayenge
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
