package com.example.campaign.segment.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

import com.example.campaign.segment.enums.ImportStatus;

@Entity
@Table(name = "segments")
@Getter
@Setter
@FieldNameConstants
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private Integer totalRows;
    private Integer successCount;
    private Integer failedCount;

    @Enumerated(EnumType.STRING)
    private ImportStatus importStatus;

    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
