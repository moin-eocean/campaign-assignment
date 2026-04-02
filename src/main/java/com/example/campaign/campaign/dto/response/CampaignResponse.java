package com.example.campaign.campaign.dto.response;

import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.enums.CampaignType;
import com.example.campaign.campaign.enums.MessageType;
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
    private MessageType messageType;
    private String messageContent;
}
