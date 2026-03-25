package com.example.campaign.campaign.controller;

import com.example.campaign.campaign.dto.request.CampaignCreateRequest;
import com.example.campaign.campaign.dto.response.CampaignResponse;
import com.example.campaign.campaign.service.CampaignService;
import com.example.campaign.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
