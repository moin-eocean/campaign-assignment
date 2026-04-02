package com.example.campaign.campaign.dto.request;

import com.example.campaign.campaign.enums.CampaignType;
import com.example.campaign.campaign.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CampaignUpdateRequest {

    @NotBlank(message = "Campaign name is required")
    private String name;

    @NotNull(message = "Campaign type is required")
    private CampaignType type;

    private LocalDateTime scheduledAt;

    private List<Long> segmentIds;

    private List<Long> contactIds;

    @NotNull(message = "Message type is required")
    private MessageType messageType;

    @NotBlank(message = "Message content is required")
    private String messageContent;
}
