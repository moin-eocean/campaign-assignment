package com.example.campaign.campaign.service;

import com.example.campaign.campaign.repository.CampaignMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignMessageService {

    private final CampaignMessageRepository campaignMessageRepository;

    // TODO: Add business logic for managing campaign messages
}
