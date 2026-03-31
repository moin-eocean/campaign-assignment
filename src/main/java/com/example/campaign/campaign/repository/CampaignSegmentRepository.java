package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.CampaignSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignSegmentRepository extends JpaRepository<CampaignSegment, Long> {

    List<CampaignSegment> findAllByCampaignId(Long campaignId);

    boolean existsByCampaignIdAndSegmentId(Long campaignId, String segmentId);
    
    boolean existsBySegmentId(Long segmentId);
}
