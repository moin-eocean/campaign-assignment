package com.example.campaign.segment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSegmentRequest {
    @NotBlank(message = "Segment name is required")
    private String name;
    
    private String description;
}
