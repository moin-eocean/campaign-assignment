package com.example.campaign.contact.repository;

import com.example.campaign.contact.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long>,
        JpaSpecificationExecutor<Contact> {

    boolean existsByPhone(String phone);

    List<Contact> findByPhoneIn(List<String> phones);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}
