package com.example.campaign.contact.service;

import com.example.campaign.common.exception.ResourceNotFoundException;
import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.common.exception.DuplicateResourceException;
import com.example.campaign.common.exception.FileProcessingException;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.contact.dto.request.ContactSearchRequest;
import com.example.campaign.contact.dto.request.ContactUpdateRequest;
import com.example.campaign.contact.dto.response.BulkUploadResponse;
import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.contact.dto.response.RowError;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.parser.CsvContactParser;
import com.example.campaign.contact.parser.ExcelContactParser;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.contact.specification.ContactSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final CsvContactParser csvParser;
    private final ExcelContactParser excelParser;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{11}$");

    public ContactResponse create(ContactCreateRequest request) {
        log.info("Creating contact{}{}", request.getName(), request.getPhone());
        boolean isNumberExist = contactRepository.existsByPhone(request.getPhone());
        if (isNumberExist) {
            throw new DuplicateResourceException("Contact", "phone", request.getPhone());
        }
        Contact contact = new Contact();
        contact.setName(request.getName());
        contact.setPhone(request.getPhone());
        contact = contactRepository.save(contact);
        log.info("contact create by id {}", contact.getId());
        return ContactResponse.toResponse(contact);
    }

    public ContactResponse update(Long id, ContactUpdateRequest request) {

        log.info("Updating contact id {}", id);

        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", "id", id));

        boolean phoneExists = contactRepository.existsByPhoneAndIdNot(request.getPhone(), id);

        if (phoneExists) {
            throw new DuplicateResourceException("Contact", "phone", request.getPhone());
        }

        contact.setName(request.getName());
        contact.setPhone(request.getPhone());

        contact = contactRepository.save(contact);

        log.info("Contact updated id {}", contact.getId());

        return ContactResponse.toResponse(contact);
    }

    public void delete(Long id) {

        log.info("Deleting contact id {}", id);

        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", "id", id));

        contactRepository.deleteById(contact.getId());

        log.info("Contact deleted id {}", id);
    }

    public ContactResponse findById(Long id) {
        log.info("Fetching contact by id {}", id);

        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", "id", id));

        return ContactResponse.toResponse(contact);
    }

    public List<ContactResponse> findAll() {
        log.info("Fetching all contacts");

        return contactRepository.findAll()
                .stream()
                .map(ContactResponse::toResponse)
                .toList();
    }

    public PagedResponse<ContactResponse> search(ContactSearchRequest request) {
        log.info("Searching contacts — search={}", request.toString());

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(request.getSortDirection())
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                request.getSortBy());

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<Contact> spec = ContactSpecification.buildSearchSpec(request);

        Page<Contact> page = contactRepository.findAll(spec, pageable);

        List<ContactResponse> content = page.getContent()
                .stream()
                .map(ContactResponse::toResponse)
                .toList();

        return PagedResponse.<ContactResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    public void upload(MultipartFile file) {

        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();

            processFileAsync(fileBytes, originalFilename);
        } catch (Exception ex) {
            throw new FileProcessingException("Failed to start background file processing", ex);
        }
    }

    @Async
    public void processFileAsync(byte[] fileBytes, String originalFilename) {
        List<RowError> errors = new ArrayList<>();
        List<Contact> validContacts = new ArrayList<>();

        try {
            List<ContactCreateRequest> rows = parseFile(new java.io.ByteArrayInputStream(fileBytes), originalFilename);

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
            contactRepository.saveAll(validContacts);

            log.info("Async upload completed. Total: {}, Success: {}, Failed: {}",
                    rows.size(), validContacts.size(), errors.size());

        } catch (Exception ex) {
            log.error("Background file processing failed", ex);
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
}
