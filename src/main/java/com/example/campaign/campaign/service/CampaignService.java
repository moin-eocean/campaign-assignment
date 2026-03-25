package com.example.campaign.campaign.service;

import com.example.campaign.campaign.dto.request.CampaignCreateRequest;
import com.example.campaign.campaign.dto.response.CampaignResponse;
import com.example.campaign.campaign.entity.Campaign;
import com.example.campaign.campaign.entity.CampaignContact;
import com.example.campaign.campaign.entity.CampaignMessage;
import com.example.campaign.campaign.enums.CampaignStatus;
import com.example.campaign.campaign.enums.CampaignType;
import com.example.campaign.campaign.repository.CampaignContactRepository;
import com.example.campaign.campaign.repository.CampaignMessageRepository;
import com.example.campaign.campaign.repository.CampaignRepository;
import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.repository.ContactRepository;
import com.example.campaign.scheduler.service.CampaignSchedulerService;
import com.example.campaign.common.service.CampaignRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ContactRepository contactRepository;
    private final CampaignContactRepository campaignContactRepository;
    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignProducer campaignProducer;
    private final CampaignSchedulerService campaignSchedulerService;
    private final CampaignRedisService campaignRedisService;

    @Transactional
    public CampaignResponse createCampaign(CampaignCreateRequest request) {
        log.info("Creating campaign: {}", request.getName());

        Campaign campaign = new Campaign();
        campaign.setName(request.getName());
        campaign.setType(request.getType());
        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setScheduledAt(request.getScheduledAt());
        campaign = campaignRepository.save(campaign);

        CampaignMessage message = new CampaignMessage();
        message.setCampaign(campaign);
        message.setType(request.getMessageType());
        message.setContent(request.getMessageContent());
        campaignMessageRepository.save(message);

        List<Contact> contacts = contactRepository.findAllByIdIn(request.getContactIds());
        Campaign finalCampaign = campaign;
        List<CampaignContact> campaignContacts = contacts.stream()
                .map(contact -> {
                    CampaignContact campaignContact = new CampaignContact();
                    campaignContact.setCampaign(finalCampaign);
                    campaignContact.setContact(contact);
                    campaignContact.setProcessed(false);
                    return campaignContact;
                })
                .collect(Collectors.toList());
        campaignContactRepository.saveAll(campaignContacts);

        log.info("Campaign created successfully with ID: {} and {} contacts", campaign.getId(), contacts.size());

        if (campaign.getType().equals(CampaignType.IMMEDIATE)) {
            try {
                campaignRedisService.loadCampaignDataIntoRedis(campaign.getId());
                campaign.setStatus(CampaignStatus.RUNNING);
                campaignRepository.save(campaign);

                campaignProducer.sendCampaign(campaign.getId());

                log.info("[CampaignService] IMMEDIATE campaign published to queue. campaignId={}",
                         campaign.getId());

            } catch (Exception e) {
                // Redis ya RabbitMQ fail ho gaya
                log.error("[CampaignService] Failed to process IMMEDIATE campaign. campaignId={}, reason={}",
                          campaign.getId(), e.getMessage(), e);

                // Campaign FAILED mark karo
                campaign.setStatus(CampaignStatus.FAILED);
                campaignRepository.save(campaign);
                
                // Exception throw NAHI karna — client ko FAILED campaign return karo
            }
        } else if (campaign.getType().equals(CampaignType.SCHEDULED)) {
            try {
                campaign.setStatus(CampaignStatus.SCHEDULED);
                campaignRepository.save(campaign);
                campaignSchedulerService.schedule(campaign.getId(), campaign.getScheduledAt());
            } catch (Exception e) {
                log.error("Failed to schedule campaign: {}", campaign.getId(), e);
                // Handle appropriately, or throw
            }
        }

        return CampaignResponse.toResponse(campaign);
    }
}
