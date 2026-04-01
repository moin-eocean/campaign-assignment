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

@RestController
@RequiredArgsConstructor
@RequestMapping("/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.createCampaign(request), "Campaign created successfully"));
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
