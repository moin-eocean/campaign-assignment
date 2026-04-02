package com.example.campaign.segment.repository;

import com.example.campaign.segment.entity.SegmentContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;


@Repository
public interface SegmentContactRepository extends JpaRepository<SegmentContact, Long> {

    Page<SegmentContact> findAllBySegmentId(Long segmentId, Pageable pageable);

    boolean existsBySegmentIdAndContactId(Long segmentId, Long contactId);

    void deleteBySegmentIdAndContactId(Long segmentId, Long contactId);

    List<SegmentContact> findAllBySegmentIdIn(Set<Long> segmentIds);
}
