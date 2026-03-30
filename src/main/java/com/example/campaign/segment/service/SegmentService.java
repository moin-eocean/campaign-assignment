package com.example.campaign.segment.service;

import com.example.campaign.campaign.repository.CampaignSegmentRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.example.campaign.common.exception.DuplicateResourceException;
import com.example.campaign.common.exception.FileProcessingException;
import com.example.campaign.common.exception.ResourceNotFoundException;
import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.segment.enums.ImportStatus;
import com.example.campaign.segment.repository.BulkContactInsertRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.contact.dto.response.RowError;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.segment.dto.request.SegmentSearchRequest;
import com.example.campaign.segment.dto.response.SegmentResponse;
import com.example.campaign.segment.entity.Segment;
import com.example.campaign.segment.entity.SegmentContact;
import com.example.campaign.segment.parser.CsvContactParser;
import com.example.campaign.segment.parser.ExcelContactParser;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;
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
    private final CsvContactParser csvParser;
    private final ExcelContactParser excelParser;
    private final BulkContactInsertRepository bulkInsertRepository;
    private final ProgressTrackingService progressTrackingService;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,15}$");

    @Async("uploadTaskExecutor")
    public CompletableFuture<String> upload(MultipartFile file, String segmentName) {
    
        String jobId = UUID.randomUUID().toString();
    
        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
    
            // Parse to get totalRows for progress init
            List<ContactCreateRequest> rows = parseFile(
                new ByteArrayInputStream(fileBytes), originalFilename
            );
    
            String finalSegmentName = (segmentName != null && !segmentName.isBlank())
                ? segmentName
                : "Import - " + LocalDate.now();
    
            // Init Redis — status: QUEUED
            progressTrackingService.init(jobId, finalSegmentName, rows.size());
    
            // Create Segment in DB immediately — status: PROCESSING
            Segment segment = new Segment();
            segment.setName(finalSegmentName);
            segment.setImportStatus(ImportStatus.PROCESSING);
            segment = segmentRepository.save(segment);
    
            // Process chunks in background
            processInChunks(jobId, rows, segment);
    
        } catch (Exception ex) {
            log.error("Upload initiation failed. jobId: {}", jobId, ex);
            progressTrackingService.markFailed(jobId, ex.getMessage());
        }
    
        return CompletableFuture.completedFuture(jobId);
    }

    private void processInChunks(String jobId, List<ContactCreateRequest> rows, Segment segment) {
    
        final int CHUNK_SIZE = 1500;
        final int totalRows = rows.size();
    
        List<RowError> allErrors = new ArrayList<>();
        List<Long> allInsertedIds = new ArrayList<>();
    
        int processedRows = 0;
        Set<String> seenPhones = ConcurrentHashMap.newKeySet(); // in-file dedup
    
        List<List<ContactCreateRequest>> chunks = partition(rows, CHUNK_SIZE);
    
        try {
            for (List<ContactCreateRequest> chunk : chunks) {
    
                List<RowError> chunkErrors = new ArrayList<>();
                List<Contact> chunkContacts = new ArrayList<>();
    
                for (ContactCreateRequest row : chunk) {
                    try {
                        validate(row);
    
                        if (!seenPhones.add(row.getPhone())) {
                            throw new DuplicateResourceException("Contact", "phone", row.getPhone());
                        }
    
                        Contact c = new Contact();
                        c.setName(row.getName());
                        c.setPhone(row.getPhone());
                        chunkContacts.add(c);
    
                    } catch (Exception ex) {
                        chunkErrors.add(new RowError(processedRows + 1, ex.getMessage()));
                    }
                    processedRows++;
                }

                // ✅ Existing contacts ke IDs yahan aayenge
                List<Long> existingIds = new ArrayList<>();
                removeDbDuplicatesChunked(chunkContacts, chunkErrors, existingIds);

                // New contacts insert karo → new IDs
                List<Long> newIds = bulkInsertRepository
                        .batchInsertContactsAndGetIds(chunkContacts);

                // ✅ Dono merge karo — new + existing
                List<Long> chunkIds = new ArrayList<>();
                chunkIds.addAll(newIds);
                chunkIds.addAll(existingIds);
    
                // Step 2 — INSERT segment_contacts using captured IDs (no SELECT)
                bulkInsertRepository
                    .batchInsertSegmentContacts(segment.getId(), chunkIds);

                allInsertedIds.addAll(chunkIds);
                allErrors.addAll(chunkErrors);
    
                // Update Redis after every chunk
                progressTrackingService.updateProgress(
                    jobId,
                    processedRows,
                    allInsertedIds.size(),
                    allErrors.size(),
                    totalRows,
                    chunkErrors
                );
    
                log.info("JobId: {} | Progress: {}/{} | Success: {} | Failed: {}",
                    jobId, processedRows, totalRows, allInsertedIds.size(), allErrors.size());
            }
    
            // Save final stats to Segment in DB (permanent record)
            segment.setTotalRows(totalRows);
            segment.setSuccessCount(allInsertedIds.size());
            segment.setFailedCount(allErrors.size());
            segment.setImportStatus(ImportStatus.COMPLETED);
            segment.setCompletedAt(LocalDateTime.now());
            segmentRepository.save(segment);
    
            // Mark Redis job complete
            progressTrackingService.markCompleted(
                jobId,
                segment.getId(),
                segment.getName(),
                totalRows,
                allInsertedIds.size(),
                allErrors.size()
            );
    
            log.info("JobId: {} | Upload COMPLETED | Success: {} | Failed: {}",
                jobId, allInsertedIds.size(), allErrors.size());
    
        } catch (Exception ex) {
            log.error("JobId: {} | Upload FAILED", jobId, ex);
    
            segment.setImportStatus(ImportStatus.FAILED);
            segment.setCompletedAt(LocalDateTime.now());
            segmentRepository.save(segment);
    
            progressTrackingService.markFailed(jobId, ex.getMessage());
        }
    }

    private void removeDbDuplicatesChunked(List<Contact> contacts,
                                           List<RowError> errors,
                                           List<Long> existingContactIds) { // ← new param
        if (contacts.isEmpty()) return;

        int chunkSize = 1000;
        List<String> allPhones = contacts.stream().map(Contact::getPhone).toList();

        Map<String, Long> existingPhoneToId = new HashMap<>(); // phone → id

        for (int i = 0; i < allPhones.size(); i += chunkSize) {
            List<String> chunk = allPhones.subList(i, Math.min(i + chunkSize, allPhones.size()));
            contactRepository.findByPhoneIn(chunk)
                    .forEach(c -> existingPhoneToId.put(c.getPhone(), c.getId())); // ← ID bhi capture
        }

        contacts.removeIf(contact -> {
            if (existingPhoneToId.containsKey(contact.getPhone())) {

                // ✅ Contacts table mein dobara insert mat karo
                // ✅ Lekin existing ID capture karo — segment_contacts mein jayega
                existingContactIds.add(existingPhoneToId.get(contact.getPhone()));

                // Error nahi — ye valid case hai, bas already exist karta hai
                return true;
            }
            return false;
        });
    }
    
    private <T> List<List<T>> partition(List<T> list, int size) {
        if (list == null) return Collections.emptyList();
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    private List<ContactCreateRequest> parseFile(java.io.InputStream inputStream, String name) throws Exception {

        if (name != null && name.endsWith(".csv")) {
            return csvParser.parse(inputStream);
        }

        if (name != null && name.endsWith(".xlsx")) {
            return excelParser.parse(inputStream);
        }

        throw new FileProcessingException("Unsupported file type: " + name);
    }

    private void validate(ContactCreateRequest row) {

        if (row.getName() == null || row.getName().isBlank()) {
            throw new ValidationFailedException("Name is required");
        }

        if (!PHONE_PATTERN.matcher(row.getPhone()).matches()) {
            throw new ValidationFailedException("Invalid phone format");
        }
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
