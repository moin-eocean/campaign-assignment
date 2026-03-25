package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.CampaignContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CampaignContactRepository extends JpaRepository<CampaignContact, Long> {

    @Query("SELECT c.phone FROM CampaignContact cc JOIN cc.contact c WHERE cc.campaign.id = :campaignId AND cc.processed = false")
    List<String> findPhoneNumbersByCampaignId(@Param("campaignId") Long campaignId);
}
