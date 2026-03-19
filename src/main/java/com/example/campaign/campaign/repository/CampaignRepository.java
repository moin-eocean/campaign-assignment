package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CampaignRepository extends JpaRepository<Campaign, Long>,
        JpaSpecificationExecutor<Campaign> {

}
