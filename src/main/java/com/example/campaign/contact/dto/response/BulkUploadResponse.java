package com.example.campaign.contact.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkUploadResponse {

    private int totalRows;
    private int successCount;
    private int failedCount;
    private List<RowError> errors;

}