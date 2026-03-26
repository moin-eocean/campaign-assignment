package com.example.campaign.campaign.producer;

import com.example.campaign.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendCampaign(Long campaignId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CAMPAIGN_EXCHANGE,
                RabbitMQConfig.CAMPAIGN_ROUTING_KEY,
                campaignId
        );
        log.info("Campaign sent: {}", campaignId);
    }
}
