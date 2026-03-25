package com.example.campaign.contact.parser;

import com.example.campaign.contact.dto.request.ContactCreateRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvContactParser implements ContactFileParser {

    @Override
    public List<ContactCreateRequest> parse(java.io.InputStream inputStream) throws Exception {

        List<ContactCreateRequest> contacts = new ArrayList<>();

        Reader reader = new InputStreamReader(inputStream);

        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(reader);

        for (CSVRecord record : records) {

            ContactCreateRequest req = new ContactCreateRequest();
            req.setName(record.get("name"));
            req.setPhone(record.get("phone"));

            contacts.add(req);
        }

        return contacts;
    }
}