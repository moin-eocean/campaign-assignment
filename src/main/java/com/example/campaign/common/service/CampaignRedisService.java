package com.example.campaign.common.service;

import com.example.campaign.campaign.entity.CampaignMessage;
import com.example.campaign.campaign.dto.response.CampaignProgressResponse;
import com.example.campaign.campaign.entity.CampaignSegment;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.repository.CampaignMessageRepository;
import com.example.campaign.campaign.repository.CampaignSegmentRepository;
import com.example.campaign.common.constant.Constants;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.segment.entity.SegmentContact;
import com.example.campaign.segment.repository.SegmentContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignSegmentRepository campaignSegmentRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final ContactRepository contactRepository;

    public void loadCampaignDataIntoRedis(Long campaignId) {
        log.info("[Redis Preload] Starting data preload for campaignId={}", campaignId);

        loadContacts(campaignId);
        loadMessage(campaignId);
        setInitialStatus(campaignId);

        log.info("[Redis Preload] Completed data preload for campaignId={}", campaignId);
    }

    private void loadContacts(Long campaignId) {
        String contactsKey = String.format(Constants.REDIS_CONTACTS_KEY, campaignId);

        // Idempotency check
        Long existingCount = redisTemplate.opsForList().size(contactsKey);
        if (existingCount != null && existingCount > 0) {
            log.warn("[Redis Preload] Contacts already in Redis for campaignId={}, skipping.", campaignId);
            return;
        }

        // ── Step 1: Segment contacts ──────────────────────────
        LinkedHashSet<Long> allContactIds = new LinkedHashSet<>();

        List<CampaignSegment> segments = campaignSegmentRepository
                .findAllByCampaignId(campaignId);

        Set<Long> segmentIds = segments.stream()
                .map(s -> s.getSegment().getId())
                .collect(Collectors.toSet()); // Set → cleaner

        List<SegmentContact> allSegmentContacts = segmentContactRepository
                .findAllBySegmentIdIn(segmentIds);

        allSegmentContacts
                .forEach(segmentContact -> allContactIds.add(segmentContact.getContact().getId()));

        log.info("[Redis Preload] {} unique contact IDs from segments for campaignId={}",
                allContactIds.size(), campaignId);

        if (allContactIds.isEmpty()) {
            log.warn("[Redis Preload] No contacts found for campaignId={}", campaignId);
            return;
        }

        // ── Step 3: Fetch phone numbers ───────────────────────
        List<String> phoneNumbers = contactRepository
                .findPhoneNumbersByIdIn(allContactIds);

        // ── Step 4: Push to Redis LIST ────────────────────────
        redisTemplate.opsForList().rightPushAll(contactsKey, phoneNumbers);

        log.info("[Redis Preload] {} phone numbers pushed to Redis for campaignId={}",
                phoneNumbers.size(), campaignId);

        // ── Step 5: Initialize stats HASH ─────────────────────
        String statsKey = String.format(Constants.REDIS_STATS_KEY, campaignId);
        redisTemplate.opsForHash().put(statsKey, "total", String.valueOf(phoneNumbers.size()));
        redisTemplate.opsForHash().put(statsKey, "sent", "0");
        redisTemplate.opsForHash().put(statsKey, "failed", "0");

        log.info("[Redis Preload] Stats initialized for campaignId={}", campaignId);
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

    // ─── Methods used by ContactExecutor ─────────────────────

    public String lpopContact(Long campaignId) {
        String key = String.format(Constants.REDIS_CONTACTS_KEY, campaignId);
        return redisTemplate.opsForList().leftPop(key);
    }

    public String getCampaignMessage(Long campaignId) {
        String key = String.format(Constants.REDIS_MESSAGE_KEY, campaignId);
        return redisTemplate.opsForValue().get(key);
    }

    public String getCampaignStatus(Long campaignId) {
        String key = String.format(Constants.REDIS_STATUS_KEY, campaignId);
        return redisTemplate.opsForValue().get(key);
    }

    public void setCampaignStatus(Long campaignId, String status) {
        String key = String.format(Constants.REDIS_STATUS_KEY, campaignId);
        redisTemplate.opsForValue().set(key, status);
    }

    public void incrementStat(Long campaignId, String field) {
        String key = String.format(Constants.REDIS_STATS_KEY, campaignId);
        redisTemplate.opsForHash().increment(key, field, 1);
    }

    public void markContactSent(Long campaignId, String phone, long timestamp) {
        String key = String.format(Constants.REDIS_CONTACTS_SENT_KEY, campaignId);
        redisTemplate.opsForHash().put(key, phone, String.valueOf(timestamp));
    }

    public void markContactFailed(Long campaignId, String phone, String reasonWithTimestamp) {
        String key = String.format(Constants.REDIS_CONTACTS_FAILURES_KEY, campaignId);
        redisTemplate.opsForHash().put(key, phone, reasonWithTimestamp);
    }

    public CampaignProgressResponse getCampaignProgress(Long campaignId) {
        String status = getCampaignStatus(campaignId);
        if (status == null) {
            return null;
        }

        String statsKey = String.format(Constants.REDIS_STATS_KEY, campaignId);
        
        List<Object> fields = List.of("total", "sent", "failed");
        List<Object> values = redisTemplate.opsForHash().multiGet(statsKey, fields);

        int totalContacts = values.get(0) != null ? Integer.parseInt((String) values.get(0)) : 0;
        int sentCount = values.get(1) != null ? Integer.parseInt((String) values.get(1)) : 0;
        int failedCount = values.get(2) != null ? Integer.parseInt((String) values.get(2)) : 0;

        int processedContacts = sentCount + failedCount;
        int percentage = totalContacts > 0 ? (int) ((processedContacts * 100.0) / totalContacts) : 0;

        return CampaignProgressResponse.builder()
                .campaignId(campaignId)
                .status(status)
                .totalContacts(totalContacts)
                .processedContacts(processedContacts)
                .sentCount(sentCount)
                .failedCount(failedCount)
                .progressPercentage(percentage)
                .build();
    }
}
