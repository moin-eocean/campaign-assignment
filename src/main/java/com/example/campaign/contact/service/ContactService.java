package com.example.campaign.contact.service;

import com.example.campaign.common.exception.ResourceNotFoundException;
import com.example.campaign.common.exception.DuplicateResourceException;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.contact.dto.request.ContactSearchRequest;
import com.example.campaign.contact.dto.request.ContactUpdateRequest;
import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.segment.parser.CsvContactParser;
import com.example.campaign.segment.parser.ExcelContactParser;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.contact.specification.ContactSpecification;
import com.example.campaign.segment.repository.SegmentContactRepository;
import com.example.campaign.segment.repository.SegmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;

    private final SegmentRepository segmentRepository;
    private final SegmentContactRepository segmentContactRepository;

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


}
