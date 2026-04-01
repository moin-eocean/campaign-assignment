package com.example.campaign.segment.service;

import com.example.campaign.contact.dto.response.RowError;
import com.example.campaign.segment.dto.response.UploadJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProgressTrackingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "upload:segment:";
    private static final Duration TTL = Duration.ofHours(2);

    private String key(Long segmentId) {
        return KEY_PREFIX + segmentId;
    }

    public void init(Long segmentId, String segmentName, int totalRows) {
        UploadJobStatus status = UploadJobStatus.builder()
            .segmentId(segmentId)
            .status(UploadJobStatus.Status.QUEUED)
            .segmentName(segmentName)
            .totalRows(totalRows)
            .processedRows(0)
            .successCount(0)
            .failedCount(0)
            .percentage(0)
            .errors(new ArrayList<>())
            .build();
        save(segmentId, status);
    }

    public void updateTotalRows(Long segmentId, int totalRows) {
        UploadJobStatus status = get(segmentId);
        if (status == null) return;

        status.setTotalRows(totalRows);
        status.setStatus(UploadJobStatus.Status.PROCESSING);
        save(segmentId, status);
    }

    public void updateProgress(Long segmentId, int processedRows,
                                int successCount, int failedCount,
                                int totalRows, List<RowError> chunkErrors) {

        UploadJobStatus status = get(segmentId);
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

        save(segmentId, status);
    }

    public void markCompleted(Long segmentId,
                               int totalRows, int successCount, int failedCount) {
        UploadJobStatus status = get(segmentId);
        if (status == null) return;

        status.setStatus(UploadJobStatus.Status.COMPLETED);
        status.setPercentage(100);
        status.setProcessedRows(totalRows);
        status.setTotalRows(totalRows);
        status.setSuccessCount(successCount);
        status.setFailedCount(failedCount);

        save(segmentId, status);
    }

    public void markFailed(Long segmentId, String errorMessage) {
        UploadJobStatus status = get(segmentId);
        if (status == null) return;

        status.setStatus(UploadJobStatus.Status.FAILED);
        status.setErrorMessage(errorMessage);

        save(segmentId, status);
    }

    public UploadJobStatus get(Long segmentId) {
        Object raw = redisTemplate.opsForValue().get(key(segmentId));
        if (raw == null) return null;
        if (raw instanceof UploadJobStatus status) return status;
        return objectMapper.convertValue(raw, UploadJobStatus.class);
    }

    private void save(Long segmentId, UploadJobStatus status) {
        redisTemplate.opsForValue().set(key(segmentId), status, TTL);
    }
}
