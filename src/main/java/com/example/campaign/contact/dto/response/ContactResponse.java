package com.example.campaign.contact.dto.response;

import com.example.campaign.contact.entity.Contact;
import com.example.campaign.contact.enums.ContactStatus;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ContactResponse {

    private Long id;
    private String name;
    private String phone;
    private ContactStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private ContactResponse(){}

    public static ContactResponse toResponse(Contact contact) {
        ContactResponse  res = new ContactResponse();
        res.setId(contact.getId());
        res.setName(contact.getName());
        res.setPhone(contact.getPhone());
        res.setStatus(contact.getStatus());
        res.setCreatedAt(contact.getCreatedAt());
        res.setUpdatedAt(contact.getUpdatedAt());
        return res;
    }

}