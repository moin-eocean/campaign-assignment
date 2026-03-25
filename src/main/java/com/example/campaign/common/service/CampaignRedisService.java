package com.example.campaign.common.service;

import com.example.campaign.campaign.entity.CampaignMessage;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.repository.CampaignContactRepository;
import com.example.campaign.campaign.repository.CampaignMessageRepository;
import com.example.campaign.scheduler.constant.SchedulerConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Redis mein campaign ka data load karta hai.
 * DataPreloadJob yahi call karega.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CampaignContactRepository campaignContactRepository;
    private final CampaignMessageRepository campaignMessageRepository;
    private final ObjectMapper objectMapper;

    /**
     * Campaign ka poora data DB se Redis mein load karta hai.
     * Contacts → Redis LIST (RPUSH)
     * Message  → Redis STRING (SET as JSON)
     * Status   → PENDING
     *
     * @param campaignId campaign ka unique ID
     */
    public void loadCampaignDataIntoRedis(Long campaignId) {
        log.info("[Redis Preload] Starting data preload for campaignId={}", campaignId);

        loadContacts(campaignId);
        loadMessage(campaignId);
        setInitialStatus(campaignId);

        log.info("[Redis Preload] Completed data preload for campaignId={}", campaignId);
    }

    // ─────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────

    private void loadContacts(Long campaignId) {
        String redisKey = String.format(SchedulerConstants.REDIS_CONTACTS_KEY, campaignId);

        // Pehle check karo — already loaded hai toh dobara mat daalo (idempotent)
        Long existingCount = redisTemplate.opsForList().size(redisKey);
        if (existingCount != null && existingCount > 0) {
            log.warn("[Redis Preload] Contacts already exist in Redis for campaignId={}, skipping.", campaignId);
            return;
        }

        // DB se contacts fetch karo (phone numbers only — lean fetch)
        List<String> phoneNumbers = campaignContactRepository
                .findPhoneNumbersByCampaignId(campaignId);  // Custom query — neeche dekho

        if (phoneNumbers.isEmpty()) {
            log.warn("[Redis Preload] No contacts found in DB for campaignId={}", campaignId);
            return;
        }

        // Redis LIST mein batch RPUSH
        redisTemplate.opsForList().rightPushAll(redisKey, phoneNumbers);

        log.info("[Redis Preload] Loaded {} contacts into Redis for campaignId={}", phoneNumbers.size(), campaignId);
    }

    private void loadMessage(Long campaignId) {
        String redisKey = String.format(SchedulerConstants.REDIS_MESSAGE_KEY, campaignId);

        // Already loaded hai toh skip karo
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            log.warn("[Redis Preload] Message already exists in Redis for campaignId={}, skipping.", campaignId);
            return;
        }

        CampaignMessage message = campaignMessageRepository
                .findByCampaignId(campaignId)
                .orElseThrow(() -> new IllegalStateException(
                        "No message found for campaignId=" + campaignId));

        try {
            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.opsForValue().set(redisKey, messageJson);
            log.info("[Redis Preload] Message loaded into Redis for campaignId={}", campaignId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize campaign message for campaignId=" + campaignId, e);
        }
    }

    private void setInitialStatus(Long campaignId) {
        String statusKey = String.format(SchedulerConstants.REDIS_STATUS_KEY, campaignId);
        redisTemplate.opsForValue().set(statusKey, CampaignStatus.RUNNING.name());
    }
}
