package com.example.campaign.segment.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SegmentSearchRequest {

    private String search;

    private int page = 0;

    private int size = 20;

    private String sortBy = "createdAt";

    private String sortDirection = "desc";

    @Override
    public String toString() {
        return "SegmentSearchRequest{" +
                "search='" + search + '\'' +
                ", page=" + page +
                ", size=" + size +
                ", sortBy='" + sortBy + '\'' +
                ", sortDirection='" + sortDirection + '\'' +
                '}';
    }
}
