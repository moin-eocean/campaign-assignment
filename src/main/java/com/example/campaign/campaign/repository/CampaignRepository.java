package com.example.campaign.campaign.repository;

import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.enums.CampaignType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long>,
        JpaSpecificationExecutor<Campaign> {

    List<Campaign> findByTypeAndStatusAndScheduledAtAfter(CampaignType type, CampaignStatus status, LocalDateTime scheduledAt);

    @Modifying
    @Transactional
    @Query("UPDATE Campaign c SET c.status = :status WHERE c.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") CampaignStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Campaign c SET c.status = :status, c.totalContacts = :total, c.sentCount = :sent, c.failedCount = :failed, c.completedAt = :completedAt WHERE c.id = :id")
    void updateProgressAndStatus(
            @Param("id") Long id, 
            @Param("status") CampaignStatus status, 
            @Param("total") Integer total, 
            @Param("sent") Integer sent, 
            @Param("failed") Integer failed, 
            @Param("completedAt") LocalDateTime completedAt);
}
