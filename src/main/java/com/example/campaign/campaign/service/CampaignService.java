package com.example.campaign.campaign.service;

import com.example.campaign.campaign.dto.request.CampaignCreateRequest;
import com.example.campaign.campaign.dto.response.CampaignResponse;
import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.entity.CampaignMessage;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.enums.CampaignType;
import com.example.campaign.campaign.producer.CampaignProducer;
import com.example.campaign.campaign.repository.CampaignMessageRepository;
import com.example.campaign.campaign.repository.CampaignRepository;
import com.example.campaign.campaign.repository.CampaignSegmentRepository;
import com.example.campaign.campaign.entity.CampaignSegment;
import com.example.campaign.common.exception.ResourceNotFoundException;
import com.example.campaign.common.exception.InvalidCampaignStateException;
import com.example.campaign.segment.repository.SegmentRepository;
import com.example.campaign.segment.entity.Segment;
import com.example.campaign.segment.service.SegmentService;
import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.scheduler.service.CampaignSchedulerService;
import com.example.campaign.common.service.CampaignRedisService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ContactRepository contactRepository;
    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignSegmentRepository campaignSegmentRepository;
    private final SegmentRepository segmentRepository;
    private final SegmentService segmentService;
    private final CampaignProducer campaignProducer;
    private final CampaignSchedulerService campaignSchedulerService;
    private final CampaignRedisService campaignRedisService;

    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest request) {
        log.info("Creating campaign: {}", request.getName());

        if(request.getSegmentIds().isEmpty() && request.getContactIds().isEmpty()){
            throw new ValidationFailedException("Segment IDs or Contact IDs are required");
        }

        Campaign campaign = new Campaign();
        campaign.setName(request.getName());
        campaign.setType(request.getType());
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setScheduledAt(request.getScheduledAt());
        campaign = campaignRepository.save(campaign);

        CampaignMessage message = new CampaignMessage();
        message.setCampaign(campaign);
        message.setType(request.getMessageType());
        message.setContent(request.getMessageContent());
        campaignMessageRepository.save(message);

        Campaign finalCampaign = campaign;
        if (request.getSegmentIds() != null && !request.getSegmentIds().isEmpty()) {
            List<Segment> segments = segmentRepository.findAllById(request.getSegmentIds());
            if (segments.size() != request.getSegmentIds().size()) {
                throw new ResourceNotFoundException("One or more Segments not found");
            }
            List<CampaignSegment> campaignSegments = segments.stream().map(segment -> {
                CampaignSegment cs = new CampaignSegment();
                cs.setCampaign(finalCampaign);
                cs.setSegment(segment);
                return cs;
            }).collect(Collectors.toList());
            campaignSegmentRepository.saveAll(campaignSegments);
        }

        if (request.getContactIds() != null && !request.getContactIds().isEmpty()) {
            List<Contact> contacts = contactRepository.findAllByIdIn(request.getContactIds());
            if (contacts.size() != request.getContactIds().size()) {
                throw new ResourceNotFoundException("One or more Contacts not found");
            }
            
            String segmentName = String.format("Campaign-%d-%s-Direct-Contacts", campaign.getId(), campaign.getName());
            Segment directSegment = segmentService.createSegmentWithContacts(segmentName, contacts);

            CampaignSegment cs = new CampaignSegment();
            cs.setCampaign(campaign);
            cs.setSegment(directSegment);
            campaignSegmentRepository.save(cs);
        }

        log.info("Campaign created successfully with ID: {}", campaign.getId());

        if (campaign.getType().equals(CampaignType.IMMEDIATE)) {
            try {
                executeCampaign(campaign.getId());
                log.info("[CampaignService] IMMEDIATE campaign processed successfully. id={}", campaign.getId());
            } catch (Exception e) {
                log.error("[CampaignService] Failed to process IMMEDIATE campaign. id={}, reason={}", campaign.getId(), e.getMessage(), e);
            }
        } else if (campaign.getType().equals(CampaignType.SCHEDULED)) {
            if(campaign.getScheduledAt() == null){
                throw new ValidationFailedException("Scheduled time is required for scheduled campaign");
            }
            try {
                campaign.setStatus(CampaignStatus.SCHEDULED);
                campaignRepository.save(campaign);
                campaignSchedulerService.schedule(campaign.getId(), campaign.getScheduledAt());
            } catch (Exception e) {
                log.error("Failed to schedule campaign: {}", campaign.getId(), e);
            }
        }
 
        return CampaignResponse.toResponse(campaign);
    }

    @Transactional
    public void executeCampaign(Long campaignId) {
        log.info("[CampaignService] Executing campaign execution sequence for campaignId={}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new EntityNotFoundException("Campaign not found with ID: " + campaignId));

        campaignRedisService.loadCampaignDataIntoRedis(campaignId);
        campaign.setStatus(CampaignStatus.RUNNING);
        campaignRepository.save(campaign);
        campaignProducer.sendCampaign(campaignId);

        log.info("[CampaignService] SUCCESS — campaignId={} published to queue and status updated to RUNNING", campaignId);
    }

    @Transactional
    public CampaignResponse pauseCampaign(Long campaignId) {
        log.info("[CampaignService] Pause requested for campaignId={}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));

        if (campaign.getStatus() != CampaignStatus.RUNNING) {
            throw new InvalidCampaignStateException(
                    String.format("Cannot pause campaign %d — current status is %s, expected RUNNING",
                            campaignId, campaign.getStatus()));
        }

        campaignRedisService.setCampaignStatus(campaignId, CampaignStatus.PAUSED.name());

        campaign.setStatus(CampaignStatus.PAUSED);
        campaignRepository.save(campaign);

        log.info("[CampaignService] Campaign {} paused successfully", campaignId);
        return CampaignResponse.toResponse(campaign);
    }

    @Transactional
    public CampaignResponse resumeCampaign(Long campaignId) {
        log.info("[CampaignService] Resume requested for campaignId={}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));

        if (campaign.getStatus() == CampaignStatus.STOPPED) {
            throw new InvalidCampaignStateException(
                    String.format("Cannot resume campaign %d — campaign has been STOPPED and cannot be restarted",
                            campaignId));
        }

        if (campaign.getStatus() != CampaignStatus.PAUSED) {
            throw new InvalidCampaignStateException(
                    String.format("Cannot resume campaign %d — current status is %s, expected PAUSED",
                            campaignId, campaign.getStatus()));
        }

        campaignRedisService.setCampaignStatus(campaignId, CampaignStatus.RUNNING.name());

        campaign.setStatus(CampaignStatus.RUNNING);
        campaignRepository.save(campaign);

        campaignProducer.sendCampaign(campaignId);

        log.info("[CampaignService] Campaign {} resumed and re-published to queue", campaignId);
        return CampaignResponse.toResponse(campaign);
    }

    @Transactional
    public CampaignResponse stopCampaign(Long campaignId) {
        log.info("[CampaignService] Stop requested for campaignId={}", campaignId);

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));

        CampaignStatus currentStatus = campaign.getStatus();

        if (currentStatus != CampaignStatus.RUNNING && currentStatus != CampaignStatus.PAUSED) {
            throw new InvalidCampaignStateException(
                    String.format("Cannot stop campaign %d — current status is %s, expected RUNNING or PAUSED",
                            campaignId, currentStatus));
        }

        campaignRedisService.setCampaignStatus(campaignId, CampaignStatus.STOPPED.name());

        campaign.setStatus(CampaignStatus.STOPPED);
        campaignRepository.save(campaign);

        log.info("[CampaignService] Campaign {} stopped successfully", campaignId);
        return CampaignResponse.toResponse(campaign);
    }
}
