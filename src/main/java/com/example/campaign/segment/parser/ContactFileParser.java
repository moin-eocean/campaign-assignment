package com.example.campaign.segment.parser;

import com.example.campaign.contact.dto.request.ContactCreateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ContactFileParser {

    List<ContactCreateRequest> parse(java.io.InputStream inputStream) throws Exception;

}