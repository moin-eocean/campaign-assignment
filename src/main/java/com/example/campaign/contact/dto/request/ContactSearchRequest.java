package com.example.campaign.contact.dto.request;

import com.example.campaign.contact.enums.ContactStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactSearchRequest {

    private String search;

    private ContactStatus status;

    private int page = 0;

    private int size = 20;

    private String sortBy = "createdAt";

    private String sortDirection = "desc";

    @Override
    public String toString() {
        return "ContactSearchRequest{" +
                "search='" + search + '\'' +
                ", status=" + status +
                ", page=" + page +
                ", size=" + size +
                ", sortBy='" + sortBy + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                '}';
    }
}
