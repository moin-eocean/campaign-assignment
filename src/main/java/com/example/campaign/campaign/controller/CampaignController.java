package com.example.campaign.campaign.controller;

import com.example.campaign.campaign.dto.response.CampaignResponse;
import com.example.campaign.campaign.dto.response.CampaignProgressResponse;
import com.example.campaign.campaign.dto.request.CampaignCreateRequest;
import com.example.campaign.campaign.service.CampaignService;
import com.example.campaign.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.campaign.campaign.dto.request.CampaignSearchRequest;
import com.example.campaign.campaign.dto.request.CampaignUpdateRequest;
import com.example.campaign.common.response.PagedResponse;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.findById(id), "Campaign retrieved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CampaignResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(campaignService.findAll(), "All campaigns retrieved successfully"));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<CampaignResponse>>> search(@RequestBody CampaignSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.search(request), "Campaign search results retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.createCampaign(request), "Campaign created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignResponse>> updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody CampaignUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.updateCampaign(id, request), "Campaign updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.ok(ApiResponse.success("Campaign deleted successfully"));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<CampaignResponse>> pauseCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.pauseCampaign(id), "Campaign paused successfully"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<CampaignResponse>> resumeCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.resumeCampaign(id), "Campaign resumed successfully"));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ApiResponse<CampaignResponse>> stopCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.stopCampaign(id), "Campaign stopped successfully"));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<CampaignProgressResponse>> getCampaignProgress(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getCampaignProgress(id), "Campaign progress retrieved successfully"));
    }
}
