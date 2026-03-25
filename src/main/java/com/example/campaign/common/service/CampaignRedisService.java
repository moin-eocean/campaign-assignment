package com.example.campaign.common.service;

import com.example.campaign.campaign.entity.CampaignMessage;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.repository.CampaignContactRepository;
import com.example.campaign.campaign.repository.CampaignMessageRepository;
import com.example.campaign.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CampaignContactRepository campaignContactRepository;
    private final CampaignMessageRepository campaignMessageRepository;

    public void loadCampaignDataIntoRedis(Long campaignId) {
        log.info("[Redis Preload] Starting data preload for campaignId={}", campaignId);

        loadContacts(campaignId);
        loadMessage(campaignId);
        setInitialStatus(campaignId);

        log.info("[Redis Preload] Completed data preload for campaignId={}", campaignId);
    }

    private void loadContacts(Long campaignId) {
        String redisKey = String.format(Constants.REDIS_CONTACTS_KEY, campaignId);

        Long existingCount = redisTemplate.opsForList().size(redisKey);
        if (existingCount != null && existingCount > 0) {
            log.warn("[Redis Preload] Contacts already exist in Redis for campaignId={}, skipping.", campaignId);
            return;
        }

        List<String> phoneNumbers = campaignContactRepository
                .findPhoneNumbersByCampaignId(campaignId);  // Custom query — neeche dekho

        if (phoneNumbers.isEmpty()) {
            log.warn("[Redis Preload] No contacts found in DB for campaignId={}", campaignId);
            return;
        }

        redisTemplate.opsForList().rightPushAll(redisKey, phoneNumbers);

        log.info("[Redis Preload] Loaded {} contacts into Redis for campaignId={}", phoneNumbers.size(), campaignId);
    }

    private void loadMessage(Long campaignId) {
        String redisKey = String.format(Constants.REDIS_MESSAGE_KEY, campaignId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            log.warn("[Redis Preload] Message already exists in Redis for campaignId={}, skipping.", campaignId);
            return;
        }

        CampaignMessage message = campaignMessageRepository
                .findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException(
                        "No message found for campaignId=" + campaignId));

        try {
            redisTemplate.opsForValue().set(redisKey, message.getContent());
            log.info("[Redis Preload] Message loaded into Redis for campaignId={}", campaignId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize campaign message for campaignId=" + campaignId, e);
        }
    }

    private void setInitialStatus(Long campaignId) {
        String statusKey = String.format(Constants.REDIS_STATUS_KEY, campaignId);
        redisTemplate.opsForValue().set(statusKey, CampaignStatus.RUNNING.name());
    }
}
