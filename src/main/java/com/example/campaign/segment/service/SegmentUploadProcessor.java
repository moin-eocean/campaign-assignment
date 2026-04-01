package com.example.campaign.segment.service;

import com.example.campaign.common.exception.DuplicateResourceException;
import com.example.campaign.common.exception.FileProcessingException;
import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.contact.dto.response.RowError;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.segment.entity.Segment;
import com.example.campaign.segment.enums.ImportStatus;
import com.example.campaign.segment.parser.CsvContactParser;
import com.example.campaign.segment.parser.ExcelContactParser;
import com.example.campaign.segment.repository.BulkContactInsertRepository;
import com.example.campaign.segment.repository.SegmentContactRepository;
import com.example.campaign.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentUploadProcessor {

    private final SegmentRepository segmentRepository;
    private final ContactRepository contactRepository;
    private final CsvContactParser csvParser;
    private final ExcelContactParser excelParser;
    private final BulkContactInsertRepository bulkInsertRepository;
    private final ProgressTrackingService progressTrackingService;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,15}$");

    @Async("uploadTaskExecutor")
    public void processUploadAsync(Long segmentId, byte[] fileBytes, 
                                    String originalFilename, String segmentName) {
        try {
            List<ContactCreateRequest> rows = parseFile(
                new ByteArrayInputStream(fileBytes), originalFilename
            );

            progressTrackingService.updateTotalRows(segmentId, rows.size());

            Segment segment = segmentRepository.findById(segmentId)
                .orElseThrow(() -> new RuntimeException("Segment not found: " + segmentId));
            segment.setImportStatus(ImportStatus.PROCESSING);
            segment = segmentRepository.save(segment);

            processInChunks(segmentId, rows, segment);

        } catch (Exception ex) {
            log.error("Async upload processing failed. segmentId: {}", segmentId, ex);
            progressTrackingService.markFailed(segmentId, ex.getMessage());
        }
    }

    private void processInChunks(Long segmentId, List<ContactCreateRequest> rows, Segment segment) {
    
        final int CHUNK_SIZE = 1500;
        final int totalRows = rows.size();
    
        List<RowError> allErrors = new ArrayList<>();
        List<Long> allInsertedIds = new ArrayList<>();
    
        int processedRows = 0;
        Set<String> seenPhones = ConcurrentHashMap.newKeySet();
    
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

                List<Long> existingIds = new ArrayList<>();
                removeDbDuplicatesChunked(chunkContacts, chunkErrors, existingIds);

                List<Long> newIds = bulkInsertRepository
                        .batchInsertContactsAndGetIds(chunkContacts);

                List<Long> chunkIds = new ArrayList<>();
                chunkIds.addAll(newIds);
                chunkIds.addAll(existingIds);
    
                bulkInsertRepository
                    .batchInsertSegmentContacts(segment.getId(), chunkIds);

                allInsertedIds.addAll(chunkIds);
                allErrors.addAll(chunkErrors);
    
                progressTrackingService.updateProgress(
                    segmentId,
                    processedRows,
                    allInsertedIds.size(),
                    allErrors.size(),
                    totalRows,
                    chunkErrors
                );
    
                log.info("SegmentId: {} | Progress: {}/{} | Success: {} | Failed: {}",
                    segmentId, processedRows, totalRows, allInsertedIds.size(), allErrors.size());
            }
    
            segment.setTotalRows(totalRows);
            segment.setSuccessCount(allInsertedIds.size());
            segment.setFailedCount(allErrors.size());
            segment.setImportStatus(ImportStatus.COMPLETED);
            segment.setCompletedAt(LocalDateTime.now());
            segmentRepository.save(segment);
    
            progressTrackingService.markCompleted(
                segmentId,
                totalRows,
                allInsertedIds.size(),
                allErrors.size()
            );
    
            log.info("SegmentId: {} | Upload COMPLETED | Success: {} | Failed: {}",
                segmentId, allInsertedIds.size(), allErrors.size());
    
        } catch (Exception ex) {
            log.error("SegmentId: {} | Upload FAILED", segmentId, ex);
    
            segment.setImportStatus(ImportStatus.FAILED);
            segment.setCompletedAt(LocalDateTime.now());
            segmentRepository.save(segment);
    
            progressTrackingService.markFailed(segmentId, ex.getMessage());
        }
    }

    private void removeDbDuplicatesChunked(List<Contact> contacts,
                                           List<RowError> errors,
                                           List<Long> existingContactIds) {
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
                existingContactIds.add(existingPhoneToId.get(contact.getPhone()));
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
}
