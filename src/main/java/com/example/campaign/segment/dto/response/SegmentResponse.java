package com.example.campaign.segment.dto.response;

import com.example.campaign.segment.entity.Segment;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.example.campaign.segment.enums.ImportStatus;

@Getter
@Setter
public class SegmentResponse {
    private Long id;
    private String name;
    private String description;
    private long contactCount;
    private LocalDateTime createdAt;

    private Integer totalRows;
    private Integer successCount;
    private Integer failedCount;
    private ImportStatus importStatus;
    private LocalDateTime completedAt;

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
        res.setTotalRows(segment.getTotalRows());
        res.setSuccessCount(segment.getSuccessCount());
        res.setFailedCount(segment.getFailedCount());
        res.setImportStatus(segment.getImportStatus());
        res.setCompletedAt(segment.getCompletedAt());
        return res;
    }
}
