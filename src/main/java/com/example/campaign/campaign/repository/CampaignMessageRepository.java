package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.CampaignMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignMessageRepository extends JpaRepository<CampaignMessage, Long> {
    Optional<CampaignMessage> findByCampaignId(Long campaignId);
}
