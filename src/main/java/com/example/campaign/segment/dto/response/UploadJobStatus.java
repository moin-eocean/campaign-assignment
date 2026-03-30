package com.example.campaign.segment.dto.response;

import com.example.campaign.contact.dto.response.RowError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadJobStatus implements Serializable {

    private String jobId;
    private Status status;

    private int totalRows;
    private int processedRows;
    private int successCount;
    private int failedCount;
    private int percentage;

    private Long segmentId;
    private String segmentName;

    private String errorMessage;
    @Builder.Default
    private List<RowError> errors = new ArrayList<>();

    public enum Status {
        QUEUED, PARSING, PROCESSING, COMPLETED, FAILED
    }
}
