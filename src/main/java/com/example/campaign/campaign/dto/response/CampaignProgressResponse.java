package com.example.campaign.campaign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignProgressResponse {
    private Long campaignId;
    private String status;
    private int totalContacts;
    private int processedContacts;
    private int sentCount;
    private int failedCount;
    private int progressPercentage;
}
