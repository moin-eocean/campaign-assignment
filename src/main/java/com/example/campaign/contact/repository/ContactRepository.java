package com.example.campaign.contact.repository;

import com.example.campaign.contact.entity.Contact;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long>,
        JpaSpecificationExecutor<Contact> {

    boolean existsByPhone(String phone);

    List<Contact> findByPhoneIn(List<String> phones);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    List<Contact> findAllByIdIn(List<Long> contactIds);

    @Query("SELECT c.phone FROM Contact c WHERE c.id IN :ids")
    List<String> findPhoneNumbersByIdIn(@Param("ids") Collection<Long> ids);
}
