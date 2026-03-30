package com.example.campaign.contact.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkImportResponse {
    private int totalRows;
    private int successCount;
    private int failedCount;
    private List<RowError> errors;
    private Long segmentId;
    private String segmentName;

}
