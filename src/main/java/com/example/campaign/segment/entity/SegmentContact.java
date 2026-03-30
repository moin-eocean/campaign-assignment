package com.example.campaign.segment.entity;

import com.example.campaign.contact.entity.Contact;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldNameConstants;

@Entity
@Table(name = "segment_contacts", uniqueConstraints = @UniqueConstraint(columnNames = {"segment_id", "contact_id"}))
@Getter
@Setter
@FieldNameConstants
public class SegmentContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false)
    private Segment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;
}
