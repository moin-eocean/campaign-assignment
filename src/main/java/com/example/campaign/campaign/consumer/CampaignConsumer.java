package com.example.campaign.campaign.consumer;

import com.example.campaign.campaign.executor.ContactExecutor;
import com.example.campaign.campaign.repository.CampaignRepository;
import com.example.campaign.campaign.repository.MessageLogRepository;
import com.example.campaign.common.service.CampaignRedisService;
import com.example.campaign.whatsapp.WhatsAppApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * RabbitMQ consumer — entry point for campaign execution.
 * <p>
 * Listens on {@code campaign.queue} with 2–10 concurrent OS threads.
 * Each message triggers a new {@link ContactExecutor} which blocks the consumer
 * thread until the campaign finishes (correct behavior — OS thread blocking is fine).
 */
@Slf4j
@Component
public class CampaignConsumer {

    private final CampaignRedisService campaignRedisService;
    private final WhatsAppApiClient whatsAppApiClient;
    private final MessageLogRepository messageLogRepository;
    private final CampaignRepository campaignRepository;
    private final Semaphore globalSemaphore;

    public CampaignConsumer(
            CampaignRedisService campaignRedisService,
            WhatsAppApiClient whatsAppApiClient,
            MessageLogRepository messageLogRepository,
            CampaignRepository campaignRepository,
            @Qualifier("whatsappGlobalSemaphore") Semaphore globalSemaphore
    ) {
        this.campaignRedisService = campaignRedisService;
        this.whatsAppApiClient = whatsAppApiClient;
        this.messageLogRepository = messageLogRepository;
        this.campaignRepository = campaignRepository;
        this.globalSemaphore = globalSemaphore;
    }

    @RabbitListener(queues = "campaign.queue", concurrency = "2-10")
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
            executor.execute();
        } catch (Exception e) {
            log.error("[CampaignConsumer] Unexpected error while executing campaign {}: {}",
                    campaignId, e.getMessage(), e);
            // Do not rethrow — message will be acknowledged and NOT requeued
        }
    }
}
