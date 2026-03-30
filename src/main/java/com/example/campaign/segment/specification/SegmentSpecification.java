package com.example.campaign.segment.specification;

import com.example.campaign.segment.dto.request.SegmentSearchRequest;
import com.example.campaign.segment.entity.Segment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class SegmentSpecification {

    private SegmentSpecification() {
    }

    public static Specification<Segment> buildSearchSpec(SegmentSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getSearch() != null && !request.getSearch().isBlank()) {
                String pattern = "%" + request.getSearch().trim().toLowerCase() + "%";

                Predicate nameLike = cb.like(cb.lower(root.get(Segment.Fields.name)), pattern);
                Predicate descriptionLike = cb.like(cb.lower(root.get(Segment.Fields.description)), pattern);

                predicates.add(cb.or(nameLike, descriptionLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
