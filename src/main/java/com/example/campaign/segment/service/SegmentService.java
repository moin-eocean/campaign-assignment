package com.example.campaign.segment.service;

import com.example.campaign.campaign.repository.CampaignSegmentRepository;


import java.util.*;
import com.example.campaign.common.exception.DuplicateResourceException;
import com.example.campaign.common.exception.ResourceNotFoundException;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.segment.enums.ImportStatus;
import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.segment.dto.request.SegmentSearchRequest;
import com.example.campaign.segment.dto.response.SegmentResponse;
import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.segment.entity.Segment;
import com.example.campaign.segment.entity.SegmentContact;
import com.example.campaign.segment.repository.SegmentContactRepository;
import com.example.campaign.segment.repository.SegmentRepository;
import com.example.campaign.segment.specification.SegmentSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final SegmentContactRepository segmentContactRepository;
    private final CampaignSegmentRepository campaignSegmentRepository;
    private final ContactRepository contactRepository;
    private final ProgressTrackingService progressTrackingService;
    private final SegmentUploadProcessor segmentUploadProcessor;

    /**
     * Synchronous entry point — reads file, generates jobId, and kicks off async processing.
     * Returns jobId immediately so the controller can respond without blocking.
     */
    public String upload(MultipartFile file, String segmentName) {
    
        String jobId = UUID.randomUUID().toString();
    
        try {
            // Read file bytes NOW (synchronously) because MultipartFile
            // is only available during the HTTP request lifecycle
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
    
            String finalSegmentName = (segmentName != null && !segmentName.isBlank())
                ? segmentName
                : "Import - " + LocalDate.now();

            progressTrackingService.init(jobId, finalSegmentName, 0);

            segmentUploadProcessor.processUploadAsync(jobId, fileBytes, originalFilename, finalSegmentName);
    
        } catch (Exception ex) {
            log.error("Upload initiation failed. jobId: {}", jobId, ex);
            progressTrackingService.markFailed(jobId, ex.getMessage());
        }
    
        return jobId;
    }

    public Segment createSegmentWithContacts(String name, List<Contact> contacts) {
        log.info("Creating segment '{}' with {} contacts", name, contacts.size());
        
        Segment segment = new Segment();
        segment.setName(name);
        segment.setImportStatus(ImportStatus.COMPLETED);
        segment.setTotalRows(contacts.size());
        segment.setSuccessCount(contacts.size());
        segment.setFailedCount(0);
        segment.setCompletedAt(LocalDateTime.now());
        segment = segmentRepository.save(segment);

        Segment finalSegment = segment;
        List<SegmentContact> segmentContacts = contacts.stream().map(contact -> {
            SegmentContact sc = new SegmentContact();
            sc.setSegment(finalSegment);
            sc.setContact(contact);
            return sc;
        }).collect(Collectors.toList());

        segmentContactRepository.saveAll(segmentContacts);

        return segment;
    }

    public List<SegmentResponse> findAll() {
        log.info("Fetching all segments");
        return segmentRepository.findAll()
                .stream()
                .map(segment -> {
                    long count = segmentRepository.countContactsBySegmentId(segment.getId());
                    return SegmentResponse.toResponse(segment, count);
                })
                .toList();
    }

    public PagedResponse<SegmentResponse> search(SegmentSearchRequest request) {
        log.info("Searching segments — search={}", request.toString());

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                request.getSortBy());

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<Segment> spec = SegmentSpecification.buildSearchSpec(request);

        Page<Segment> page = segmentRepository.findAll(spec, pageable);

        List<SegmentResponse> content = page.getContent()
                .stream()
                .map(segment -> {
                    long count = segmentRepository.countContactsBySegmentId(segment.getId());
                    return SegmentResponse.toResponse(segment, count);
                })
                .toList();

        return PagedResponse.<SegmentResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    public Page<SegmentResponse> getAllSegments(Pageable pageable) {
        log.info("Fetching all segments");
        return segmentRepository.findAll(pageable)
                .map(segment -> {
                    long count = segmentRepository.countContactsBySegmentId(segment.getId());
                    return SegmentResponse.toResponse(segment, count);
                });
    }

    public SegmentResponse getSegmentById(Long id) {
        log.info("Fetching segment by id: {}", id);
        Segment segment = segmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Segment", "id", id));

        long count = segmentRepository.countContactsBySegmentId(segment.getId());
        return SegmentResponse.toResponse(segment, count);
    }

    public void deleteSegment(Long id) {
        log.info("Deleting segment id: {}", id);
        Segment segment = segmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Segment", "id", id));

        // Check if segment is used in any campaign
        if (campaignSegmentRepository.existsBySegmentId(id)) {
            throw new ValidationFailedException("Segment is in use by one or more campaigns");
        }

        segmentRepository.delete(segment);
        log.info("Segment deleted id: {}", id);
    }

    public void addContactToSegment(Long segmentId, Long contactId) {
        log.info("Adding contact id: {} to segment id: {}", contactId, segmentId);

        Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Segment", "id", segmentId));

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", "id", contactId));

        if (segmentContactRepository.existsBySegmentIdAndContactId(segmentId, contactId)) {
            throw new DuplicateResourceException("SegmentContact", "contactId", String.valueOf(contactId));
        }

        SegmentContact sc = new SegmentContact();
        sc.setSegment(segment);
        sc.setContact(contact);

        segmentContactRepository.save(sc);
        log.info("Contact added to segment successfully");
    }

    public void removeContactFromSegment(Long segmentId, Long contactId) {
        log.info("Removing contact id: {} from segment id: {}", contactId, segmentId);

        if (!segmentRepository.existsById(segmentId)) {
            throw new ResourceNotFoundException("Segment", "id", segmentId);
        }
        if (!contactRepository.existsById(contactId)) {
            throw new ResourceNotFoundException("Contact", "id", contactId);
        }

        segmentContactRepository.deleteBySegmentIdAndContactId(segmentId, contactId);
        log.info("Contact removed from segment successfully");
    }

    public List<ContactResponse> getContactsBySegmentId(Long segmentId, Pageable pageable) {
        log.info("Fetching contacts for segment id: {}", segmentId);

        if (!segmentRepository.existsById(segmentId)) {
            throw new ResourceNotFoundException("Segment", "id", segmentId);
        }

        List<SegmentContact> segmentContacts = segmentContactRepository.findAllBySegmentId(segmentId);

        return segmentContacts.stream().map(sc -> ContactResponse.toResponse(sc.getContact())).toList();
    }
}
