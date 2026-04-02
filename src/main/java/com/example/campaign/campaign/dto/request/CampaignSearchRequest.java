package com.example.campaign.campaign.dto.request;

import com.example.campaign.campaign.enums.CampaignStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampaignSearchRequest {

    private String search;

    private CampaignStatus status;

    private int page = 0;

    private int size = 20;

    private String sortBy = "createdAt";

    private String sortDirection = "desc";

    @Override
    public String toString() {
        return "CampaignSearchRequest{" +
                "search='" + search + '\'' +
                ", status=" + status +
                ", page=" + page +
                ", size=" + size +
                ", sortBy='" + sortBy + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                '}';
    }
}
