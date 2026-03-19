package com.example.campaign.campaign.service;

import com.example.campaign.campaign.repository.CampaignContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignContactService {

    private final CampaignContactRepository campaignContactRepository;

    // TODO: Add business logic for mapping contacts to campaigns
}
