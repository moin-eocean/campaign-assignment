package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.CampaignContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignContactRepository extends JpaRepository<CampaignContact, Long> {
}
