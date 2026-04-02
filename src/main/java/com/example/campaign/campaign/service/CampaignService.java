package com.example.campaign.campaign.service;

import com.example.campaign.campaign.dto.request.CampaignCreateRequest;
import com.example.campaign.campaign.dto.response.CampaignResponse;
import com.example.campaign.campaign.dto.response.CampaignProgressResponse;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.example.campaign.campaign.dto.request.CampaignSearchRequest;
import com.example.campaign.campaign.dto.request.CampaignUpdateRequest;
import com.example.campaign.campaign.specification.CampaignSpecification;
import com.example.campaign.common.response.PagedResponse;

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

        if (request.getSegmentIds().isEmpty() && request.getContactIds().isEmpty()) {
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
            if (campaign.getScheduledAt() == null) {
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

        return toResponse(campaign);
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
        return toResponse(campaign);
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
        return toResponse(campaign);
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
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public CampaignProgressResponse getCampaignProgress(Long campaignId) {
        CampaignProgressResponse progress = campaignRedisService.getCampaignProgress(campaignId);

        if (progress == null) {
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", campaignId));

            int total = campaign.getTotalContacts() != null ? campaign.getTotalContacts() : 0;
            int sent = campaign.getSentCount() != null ? campaign.getSentCount() : 0;
            int failed = campaign.getFailedCount() != null ? campaign.getFailedCount() : 0;
            int processed = sent + failed;
            int percentage = total > 0 ? (int) ((processed * 100.0) / total) : 0;

            return CampaignProgressResponse.builder()
                    .campaignId(campaignId)
                    .status(campaign.getStatus().name())
                    .totalContacts(total)
                    .sentCount(sent)
                    .failedCount(failed)
                    .processedContacts(processed)
                    .progressPercentage(percentage)
                    .build();
        }

        return progress;
    }

    @Transactional(readOnly = true)
    public CampaignResponse findById(Long id) {
        log.info("Fetching campaign by id {}", id);
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> findAll() {
        log.info("Fetching all campaigns");
        return campaignRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedResponse<CampaignResponse> search(CampaignSearchRequest request) {
        log.info("Searching campaigns — search={}", request.toString());

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                request.getSortBy());

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        Specification<Campaign> spec = CampaignSpecification.buildSearchSpec(request);
        Page<Campaign> page = campaignRepository.findAll(spec, pageable);

        List<CampaignResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PagedResponse.<CampaignResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public CampaignResponse updateCampaign(Long id, CampaignUpdateRequest request) {
        log.info("Updating campaign id {}", id);

        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.SCHEDULED) {
            throw new InvalidCampaignStateException(
                    String.format("Cannot update campaign %d — current status is %s. Only DRAFT or SCHEDULED campaigns can be edited.",
                            id, campaign.getStatus()));
        }

        campaign.setName(request.getName());
        campaign.setType(request.getType());
        campaign.setScheduledAt(request.getScheduledAt());

        CampaignMessage message = campaignMessageRepository.findByCampaignId(id)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignMessage", "id", id));

        message.setType(request.getMessageType());
        message.setContent(request.getMessageContent());
        campaignMessageRepository.save(message);

        List<CampaignSegment> existingSegments = campaignSegmentRepository.findAllByCampaignId(id);
        campaignSegmentRepository.deleteAll(existingSegments);

        if (request.getSegmentIds() != null && !request.getSegmentIds().isEmpty()) {
            List<Segment> segments = segmentRepository.findAllById(request.getSegmentIds());
            if (segments.size() != request.getSegmentIds().size()) {
                throw new ResourceNotFoundException("One or more Segments not found");
            }
            List<CampaignSegment> campaignSegments = segments.stream().map(segment -> {
                CampaignSegment cs = new CampaignSegment();
                cs.setCampaign(campaign);
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

        if (campaign.getType() == CampaignType.IMMEDIATE) {
            campaign.setStatus(CampaignStatus.DRAFT);
        } else {
            campaign.setStatus(CampaignStatus.SCHEDULED);
            if (campaign.getScheduledAt() != null) {
                try {
                    campaignSchedulerService.schedule(id, campaign.getScheduledAt());
                } catch (Exception e) {
                    log.error("Failed to schedule updated campaign {}: {}", id, e.getMessage(), e);
                }
            }
        }

        campaignRepository.save(campaign);
        log.info("Campaign updated successfully id: {}", id);
        return toResponse(campaign);
    }

    @Transactional
    public void deleteCampaign(Long id) {
        log.info("Deleting campaign id {}", id);

        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", "id", id));

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidCampaignStateException(
                    String.format("Cannot delete campaign %d — current status is %s. Only DRAFT campaigns can be deleted.",
                            id, campaign.getStatus()));
        }
        campaignMessageRepository.findByCampaignId(id).ifPresent(campaignMessageRepository::delete);
        List<CampaignSegment> segments = campaignSegmentRepository.findAllByCampaignId(id);
        campaignSegmentRepository.deleteAll(segments);
        campaignRepository.delete(campaign);

        log.info("Campaign deleted id {}", id);
    }


    private CampaignResponse toResponse(Campaign campaign) {
        CampaignMessage message = campaignMessageRepository.findByCampaignId(campaign.getId()).orElse(null);
        CampaignResponse res = new CampaignResponse();
        res.setId(campaign.getId());
        res.setName(campaign.getName());
        res.setStatus(campaign.getStatus());
        res.setType(campaign.getType());
        res.setScheduledAt(campaign.getScheduledAt());
        res.setCreatedAt(campaign.getCreatedAt());
        res.setCompletedAt(campaign.getCompletedAt());
        res.setTotalContacts(campaign.getTotalContacts() != null ? campaign.getTotalContacts() : 0);
        res.setSentCount(campaign.getSentCount() != null ? campaign.getSentCount() : 0);
        res.setFailedCount(campaign.getFailedCount() != null ? campaign.getFailedCount() : 0);
        if (message != null) {
            res.setMessageType(message.getType());
            res.setMessageContent(message.getContent());
        }

        return res;
    }
}
