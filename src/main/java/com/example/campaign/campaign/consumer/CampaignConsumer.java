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
import java.util.concurrent.RejectedExecutionException;
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

    public CampaignConsumer(
            CampaignRedisService campaignRedisService,
            WhatsAppApiClient whatsAppApiClient,
            MessageLogRepository messageLogRepository,
            CampaignRepository campaignRepository,
            @Qualifier("whatsappGlobalSemaphore") Semaphore globalSemaphore,
            @Qualifier("campaignExecutorPool") ExecutorService campaignExecutorPool
    ) {
        this.campaignRedisService = campaignRedisService;
        this.whatsAppApiClient = whatsAppApiClient;
        this.messageLogRepository = messageLogRepository;
        this.campaignRepository = campaignRepository;
        this.globalSemaphore = globalSemaphore;
        this.campaignExecutorPool = campaignExecutorPool;
    }

    @RabbitListener(queues = "campaign.queue", concurrency = "1-10")
    public void onCampaignReceived(Long campaignId) {
        log.info("[CampaignConsumer] Received campaign: {}", campaignId);

        try {
            ContactExecutor executor = new ContactExecutor(
                    campaignId,
                    campaignRedisService,
                    whatsAppApiClient,
                    globalSemaphore,
                    messageLogRepository,
                    campaignRepository
            );

            campaignExecutorPool.submit(() -> {
                try {
                    executor.execute();
                } catch (Exception e) {
                    log.error("[CampaignConsumer] Error executing campaign {}: {}",
                            campaignId, e.getMessage(), e);
                }
            });

        } catch (RejectedExecutionException e) {
            log.warn("[CampaignConsumer] Pool full — requeueing campaign {}", campaignId);
            throw new AmqpRejectAndDontRequeueException("Campaign executor pool is full", e);
        } catch (Exception e) {
            log.error("[CampaignConsumer] Unexpected error for campaign {}: {}",
                    campaignId, e.getMessage(), e);
        }
    }
}
