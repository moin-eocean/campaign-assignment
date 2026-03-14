package com.example.campaign.contact.repository;

import com.example.campaign.contact.entity.Contact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<Contact,Long> {
    boolean existsByPhone(String phone);

    List<Contact> findByPhoneIn(List<String> phones);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}
