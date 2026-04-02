package com.example.campaign.campaign.specification;

import com.example.campaign.campaign.dto.request.CampaignSearchRequest;
import com.example.campaign.campaign.entity.Campaign;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class CampaignSpecification {

    private CampaignSpecification() {
    }

    public static Specification<Campaign> buildSearchSpec(CampaignSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getSearch() != null && !request.getSearch().isBlank()) {
                String pattern = "%" + request.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get(Campaign.Fields.name)), pattern));
            }

            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get(Campaign.Fields.status), request.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
