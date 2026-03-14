package com.example.campaign.contact.service;

import com.example.campaign.common.exception.ValidationFailedException;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.contact.dto.request.ContactUpdateRequest;
import com.example.campaign.contact.dto.response.BulkUploadResponse;
import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.contact.dto.response.RowError;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.enums.ContactStatus;
import com.example.campaign.contact.parser.CsvContactParser;
import com.example.campaign.contact.parser.ExcelContactParser;
import com.example.campaign.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final CsvContactParser csvParser;
    private final ExcelContactParser excelParser;

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[0-9]{11}$");

    public ContactResponse create(ContactCreateRequest request) {
        log.info("Creating contact{}{}", request.getName(), request.getPhone());
        boolean isNumberExist = contactRepository.existsByPhone(request.getPhone());
        if (isNumberExist) {
            throw new ValidationFailedException(request.getPhone() + "Phone number already exist");
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
                .orElseThrow(() -> new ValidationFailedException("Contact not found"));

        boolean phoneExists =
                contactRepository.existsByPhoneAndIdNot(request.getPhone(), id);

        if (phoneExists) {
            throw new ValidationFailedException("Phone number already exists");
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
                .orElseThrow(() -> new ValidationFailedException("Contact not found"));

        contactRepository.deleteById(contact.getId());

        log.info("Contact deleted id {}", id);
    }

    public BulkUploadResponse upload(MultipartFile file) {

        List<RowError> errors = new ArrayList<>();
        List<Contact> validContacts = new ArrayList<>();

        try {

            List<ContactCreateRequest> rows = parseFile(file);

            Set<String> phoneSet = new HashSet<>();

            int rowNumber = 1;

            for (ContactCreateRequest row : rows) {

                try {

                    validate(row);

                    if (phoneSet.contains(row.getPhone())) {
                        throw new RuntimeException("Duplicate phone in file");
                    }

                    phoneSet.add(row.getPhone());

                    Contact contact = Contact.builder()
                            .name(row.getName())
                            .phone(row.getPhone())
                            .build();

                    validContacts.add(contact);

                } catch (Exception ex) {

                    errors.add(new RowError(rowNumber, ex.getMessage()));
                }

                rowNumber++;
            }

            removeDbDuplicates(validContacts, errors);

            contactRepository.saveAll(validContacts);

            return BulkUploadResponse.builder()
                    .totalRows(rows.size())
                    .successCount(validContacts.size())
                    .failedCount(errors.size())
                    .errors(errors)
                    .build();

        } catch (Exception ex) {

            throw new RuntimeException("File processing failed", ex);
        }
    }

    private List<ContactCreateRequest> parseFile(MultipartFile file) throws Exception {

        String name = file.getOriginalFilename();

        if (name.endsWith(".csv")) {
            return csvParser.parse(file);
        }

        if (name.endsWith(".xlsx")) {
            return excelParser.parse(file);
        }

        throw new RuntimeException("Unsupported file type");
    }

    private void validate(ContactCreateRequest row) {

        if (row.getName() == null || row.getName().isBlank()) {
            throw new RuntimeException("Name is required");
        }

        if (!PHONE_PATTERN.matcher(row.getPhone()).matches()) {
            throw new RuntimeException("Invalid phone format");
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
