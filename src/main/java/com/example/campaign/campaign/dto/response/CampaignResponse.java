package com.example.campaign.campaign.dto.response;

import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.enums.CampaignType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
public class CampaignResponse {
    private Long id;
    private String name;
    private CampaignStatus status;
    private CampaignType type;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private int contactCount;
    private Integer totalContacts;
    private Integer sentCount;
    private Integer failedCount;

    private CampaignResponse(){
    }

    public static  CampaignResponse  toResponse(Campaign campaign){
        CampaignResponse res = new CampaignResponse();
        res.setId(campaign.getId());
        res.setName(campaign.getName());
        res.setStatus(campaign.getStatus());
        res.setType(campaign.getType());
        res.setScheduledAt(campaign.getScheduledAt());
        res.setCreatedAt(campaign.getCreatedAt());
        res.setCompletedAt(campaign.getCompletedAt());
        res.setTotalContacts(campaign.getTotalContacts() != null ? campaign.getTotalContacts() : 0);
        res.setSentCount(campaign.getSentCount() != null ? campaign.getSentCount() : 0);
        res.setFailedCount(campaign.getFailedCount() != null ? campaign.getFailedCount() : 0);
        return res;
    }
}
