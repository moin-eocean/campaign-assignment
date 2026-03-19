package com.example.campaign.campaign.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldNameConstants
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false)
    private String contactNumber;

    private String contactName;

    @Column(nullable = false)
    private String status;

    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String rawError;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    private LocalDateTime sentAt;
}
