package com.example.campaign.segment.repository;

import com.example.campaign.segment.entity.Segment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SegmentRepository extends JpaRepository<Segment, Long>, JpaSpecificationExecutor<Segment> {

    @Query("SELECT COUNT(sc) FROM SegmentContact sc WHERE sc.segment.id = :segmentId")
    long countContactsBySegmentId(@Param("segmentId") Long segmentId);
}
