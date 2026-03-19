package com.example.campaign.campaign.service;

import com.example.campaign.campaign.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageLogService {

    private final MessageLogRepository messageLogRepository;

    // TODO: Add business logic for logging messages
}
