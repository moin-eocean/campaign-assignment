package com.example.campaign.segment.service;

import com.example.campaign.campaign.repository.CampaignSegmentRepository;
import java.util.concurrent.CompletableFuture;
import com.example.campaign.common.exception.DuplicateResourceException;
import com.example.campaign.common.exception.FileProcessingException;
import com.example.campaign.common.exception.ResourceNotFoundException;
import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.contact.dto.response.BulkImportResponse;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{11}$");

    @Async
    public CompletableFuture<BulkImportResponse> upload(MultipartFile file, String segmentName) {

        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();

            return CompletableFuture.completedFuture(processFile(fileBytes, originalFilename, segmentName));
        } catch (Exception ex) {
            log.error("Failed to process file upload", ex);
            CompletableFuture<BulkImportResponse> future = new CompletableFuture<>();
            future.completeExceptionally(new FileProcessingException("Failed to process file upload", ex));
            return future;
        }
    }

    private BulkImportResponse processFile(byte[] fileBytes, String originalFilename, String segmentName) {
        List<RowError> errors = new ArrayList<>();
        List<Contact> validContacts = new ArrayList<>();

        try {
            List<ContactCreateRequest> rows = parseFile(new ByteArrayInputStream(fileBytes), originalFilename);

            Set<String> phoneSet = new HashSet<>();
            int rowNumber = 1;

            for (ContactCreateRequest row : rows) {
                try {
                    validate(row);
                    if (phoneSet.contains(row.getPhone())) {
                        throw new DuplicateResourceException("Contact", "phone", row.getPhone());
                    }
                    phoneSet.add(row.getPhone());

                    Contact contact = new Contact();
                    contact.setName(row.getName());
                    contact.setPhone(row.getPhone());
                    validContacts.add(contact);

                } catch (Exception ex) {
                    errors.add(new RowError(rowNumber, ex.getMessage()));
                }
                rowNumber++;
            }

            removeDbDuplicates(validContacts, errors);

            // Create a new Segment
            String finalSegmentName = (segmentName != null && !segmentName.isBlank())
                    ? segmentName
                    : "Import - " + java.time.LocalDate.now().toString();
            Segment segment = new Segment();
            segment.setName(finalSegmentName);
            segment = segmentRepository.save(segment);

            // Save contacts
            validContacts = contactRepository.saveAll(validContacts);

            // Generate SegmentContacts
            Segment finalSegment = segment;
            List<SegmentContact> segmentContacts = validContacts.stream().map(c -> {
                SegmentContact sc = new SegmentContact();
                sc.setSegment(finalSegment);
                sc.setContact(c);
                return sc;
            }).toList();
            segmentContactRepository.saveAll(segmentContacts);

            log.info("Upload completed. Total: {}, Success: {}, Failed: {}",
                    rows.size(), validContacts.size(), errors.size());

            return BulkImportResponse.builder()
                    .totalRows(rows.size())
                    .successCount(validContacts.size())
                    .failedCount(errors.size())
                    .errors(errors)
                    .segmentId(segment.getId())
                    .segmentName(segment.getName())
                    .build();

        } catch (Exception ex) {
            log.error("File processing failed", ex);
            throw new FileProcessingException("File processing failed", ex);
        }
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

    private void removeDbDuplicates(List<Contact> contacts,
                                    List<RowError> errors) {

        List<String> phones = contacts
                .stream()
                .map(Contact::getPhone)
                .toList();

        List<Contact> existing = contactRepository.findByPhoneIn(phones);

        Set<String> existingPhones = existing
                .stream()
                .map(Contact::getPhone)
                .collect(Collectors.toSet());

        contacts.removeIf(contact -> {

            if (existingPhones.contains(contact.getPhone())) {

                errors.add(new RowError(0,
                        "Phone already exists in DB: " + contact.getPhone()));

                return true;
            }

            return false;
        });
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
