package com.example.campaign.contact.specification;

import com.example.campaign.contact.dto.request.ContactSearchRequest;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.enums.ContactStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ContactSpecification {

    private ContactSpecification() {
    }

    public static Specification<Contact> buildSearchSpec(ContactSearchRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getSearch() != null && !request.getSearch().isBlank()) {
                String pattern = "%" + request.getSearch().trim().toLowerCase() + "%";

                Predicate nameLike = cb.like(cb.lower(root.get(Contact.Fields.name)), pattern);
                Predicate phoneLike = cb.like(cb.lower(root.get(Contact.Fields.phone)), pattern);

                predicates.add(cb.or(nameLike, phoneLike));
            }

            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get(Contact.Fields.status), request.getStatus()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
