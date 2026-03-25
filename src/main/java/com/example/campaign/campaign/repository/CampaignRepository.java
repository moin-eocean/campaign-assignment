package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.example.campaign.campaign.enums.CampaignType;
import com.example.campaign.campaign.enums.CampaignStatus;
import java.time.LocalDateTime;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long>,
        JpaSpecificationExecutor<Campaign> {

    List<Campaign> findByTypeAndStatusAndScheduledAtAfter(CampaignType type, CampaignStatus status, LocalDateTime scheduledAt);
}
