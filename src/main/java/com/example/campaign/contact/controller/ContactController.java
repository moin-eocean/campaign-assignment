package com.example.campaign.contact.controller;

import com.example.campaign.common.response.ApiResponse;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.contact.dto.request.ContactCreateRequest;
import com.example.campaign.contact.dto.request.ContactSearchRequest;
import com.example.campaign.contact.dto.request.ContactUpdateRequest;

import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.contact.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/contacts")
public class ContactController {

    private final ContactService contactService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(contactService.findAll()));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<ContactResponse>>> search(
            @RequestBody ContactSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contactService.search(request)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ContactResponse>> create(@Valid @RequestBody ContactCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contactService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ContactUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contactService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        contactService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Contact deleted successfully"));
    }
}
