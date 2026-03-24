package com.example.campaign.campaign.service;

import com.example.campaign.campaign.entity.Campaign;
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

    public void sendCampaign(Campaign campaign) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CAMPAIGN_EXCHANGE,
                RabbitMQConfig.CAMPAIGN_ROUTING_KEY,
                campaign
        );
        log.info("Campaign sent: {}", campaign);
    }
}
