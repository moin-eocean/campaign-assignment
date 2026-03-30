package com.example.campaign.segment.service;

import com.example.campaign.contact.dto.response.RowError;
import com.example.campaign.segment.dto.response.UploadJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressTrackingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "upload:job:";
    private static final Duration TTL = Duration.ofHours(2);

    private String key(String jobId) {
        return KEY_PREFIX + jobId;
    }

    public void init(String jobId, String segmentName, int totalRows) {
        UploadJobStatus status = UploadJobStatus.builder()
            .jobId(jobId)
            .status(UploadJobStatus.Status.QUEUED)
            .segmentName(segmentName)
            .totalRows(totalRows)
            .processedRows(0)
            .successCount(0)
            .failedCount(0)
            .percentage(0)
            .errors(new ArrayList<>())
            .build();
        save(jobId, status);
    }

    public void updateProgress(String jobId, int processedRows,
                                int successCount, int failedCount,
                                int totalRows, List<RowError> chunkErrors) {

        UploadJobStatus status = get(jobId);
        if (status == null) return;

        int percentage = totalRows > 0
            ? (int) ((processedRows * 100.0) / totalRows)
            : 0;

        status.setStatus(UploadJobStatus.Status.PROCESSING);
        status.setProcessedRows(processedRows);
        status.setSuccessCount(successCount);
        status.setFailedCount(failedCount);
        status.setPercentage(percentage);
        if (chunkErrors != null) {
            status.getErrors().addAll(chunkErrors);
        }

        save(jobId, status);
    }

    public void markCompleted(String jobId, Long segmentId, String segmentName,
                               int totalRows, int successCount, int failedCount) {
        UploadJobStatus status = get(jobId);
        if (status == null) return;

        status.setStatus(UploadJobStatus.Status.COMPLETED);
        status.setPercentage(100);
        status.setProcessedRows(totalRows);
        status.setSegmentId(segmentId);
        status.setSegmentName(segmentName);
        status.setTotalRows(totalRows);
        status.setSuccessCount(successCount);
        status.setFailedCount(failedCount);

        save(jobId, status);
    }

    public void markFailed(String jobId, String errorMessage) {
        UploadJobStatus status = get(jobId);
        if (status == null) return;

        status.setStatus(UploadJobStatus.Status.FAILED);
        status.setErrorMessage(errorMessage);

        save(jobId, status);
    }

    public UploadJobStatus get(String jobId) {
        return (UploadJobStatus) redisTemplate.opsForValue().get(key(jobId));
    }

    private void save(String jobId, UploadJobStatus status) {
        redisTemplate.opsForValue().set(key(jobId), status, TTL);
    }
}
