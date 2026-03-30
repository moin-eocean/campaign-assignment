package com.example.campaign.segment.dto.response;

import com.example.campaign.segment.entity.Segment;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SegmentResponse {
    private Long id;
    private String name;
    private String description;
    private long contactCount;
    private LocalDateTime createdAt;

    private SegmentResponse (){}

    public static SegmentResponse toResponse(Segment segment, long contactCount) {
        if (segment == null) {
            return null;
        }
        SegmentResponse res = new SegmentResponse();
        res.setId(segment.getId());
        res.setName(segment.getName());
        res.setDescription(segment.getDescription());
        res.setContactCount(contactCount);
        res.setCreatedAt(segment.getCreatedAt());
        return res;
    }
}
