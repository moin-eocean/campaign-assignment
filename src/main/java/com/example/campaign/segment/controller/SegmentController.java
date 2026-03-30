package com.example.campaign.segment.controller;

import java.util.concurrent.CompletableFuture;

import com.example.campaign.common.response.ApiResponse;
import com.example.campaign.common.response.PagedResponse;
import com.example.campaign.segment.dto.response.UploadJobStatus;
import com.example.campaign.segment.service.ProgressTrackingService;
import java.util.Map;
import com.example.campaign.contact.dto.response.ContactResponse;
import com.example.campaign.segment.dto.request.CreateSegmentRequest;
import com.example.campaign.segment.dto.request.SegmentSearchRequest;
import com.example.campaign.segment.dto.response.SegmentResponse;
import com.example.campaign.segment.service.SegmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/segments")
public class SegmentController {

    private final SegmentService segmentService;
    private final ProgressTrackingService progressTrackingService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "segmentName", required = false) String segmentName) throws Exception {
    
        CompletableFuture<String> future = segmentService.upload(file, segmentName);
        String jobId = future.get(); // get jobId only — processing is async
    
        return ResponseEntity.accepted().body(Map.of(
            "jobId", jobId,
            "message", "Upload started. Poll progress endpoint for status.",
            "progressUrl", "/segments/upload/" + jobId + "/progress"
        ));
    }

    @GetMapping("/upload/{jobId}/progress")
    public ResponseEntity<UploadJobStatus> getUploadProgress(@PathVariable String jobId) {
       UploadJobStatus status = progressTrackingService.get(jobId);
    
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
    
        return ResponseEntity.ok(status);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SegmentResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(segmentService.findAll()));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<SegmentResponse>>> search(
            @RequestBody SegmentSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(segmentService.search(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SegmentResponse>> getSegmentById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(segmentService.getSegmentById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteSegment(@PathVariable Long id) {
        segmentService.deleteSegment(id);
        return ResponseEntity.ok(ApiResponse.success("Segment deleted successfully"));
    }

    @PostMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<ApiResponse<String>> addContactToSegment(
            @PathVariable Long id,
            @PathVariable Long contactId) {
        segmentService.addContactToSegment(id, contactId);
        return ResponseEntity.ok(ApiResponse.success("Contact added to segment successfully"));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<ApiResponse<String>> removeContactFromSegment(
            @PathVariable Long id,
            @PathVariable Long contactId) {
        segmentService.removeContactFromSegment(id, contactId);
        return ResponseEntity.ok(ApiResponse.success("Contact removed from segment successfully"));
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContactsBySegmentId(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(segmentService.getContactsBySegmentId(id, pageable)));
    }

}
