package com.example.campaign.campaign.entity;

import com.example.campaign.segment.entity.Segment;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldNameConstants;

@Entity
@Table(name = "campaign_segments", uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "segment_id"}))
@Getter
@Setter
@FieldNameConstants
public class CampaignSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false)
    private Segment segment;
}
