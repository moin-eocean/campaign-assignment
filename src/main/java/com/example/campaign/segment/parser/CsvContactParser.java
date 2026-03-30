package com.example.campaign.segment.parser;

import com.example.campaign.contact.dto.request.ContactCreateRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CsvContactParser implements ContactFileParser {

    private static final List<String> REQUIRED_HEADERS = List.of("Name", "Phone_Number");

    @Override
    public List<ContactCreateRequest> parse(InputStream inputStream) throws Exception {

        // 1. Read entire file as string
        String rawContent = new String(inputStream.readAllBytes());

        // 2. Clean the header row
        String cleanedContent = cleanHeaders(rawContent);

        // 3. Parse normally
        List<ContactCreateRequest> contacts = new ArrayList<>();

        Iterable<CSVRecord> records = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get()
                .parse(new StringReader(cleanedContent));

        for (CSVRecord record : records) {
            ContactCreateRequest req = new ContactCreateRequest();
            req.setName(record.get("Name").trim());
            req.setPhone(record.get("Phone_Number").trim());
            contacts.add(req);
        }

        return contacts;
    }

    private String cleanHeaders(String csvContent) throws IOException {
        // Split into header line + rest of file
        int newlineIndex = csvContent.indexOf('\n');
        if (newlineIndex == -1) {
            throw new IllegalArgumentException("CSV file appears to be empty or has no data rows");
        }

        String headerLine = csvContent.substring(0, newlineIndex);
        String dataLines  = csvContent.substring(newlineIndex); // keeps the \n

        // Remove empty columns and junk characters from header
        String cleanedHeader = Arrays.stream(headerLine.split(","))
                .map(String::trim)
                .map(h -> h.replaceAll("[`~\\[\\]]", "")) // strip backticks and other junk
                .filter(h -> !h.isEmpty())
                .collect(Collectors.joining(","));

        // Validate required headers are present
        List<String> presentHeaders = Arrays.asList(cleanedHeader.split(","));
        REQUIRED_HEADERS.forEach(required -> {
            if (!presentHeaders.contains(required)) {
                throw new IllegalArgumentException(
                        "Missing required CSV column: '" + required + "'. " +
                                "Found headers: " + presentHeaders
                );
            }
        });

        return cleanedHeader + dataLines;
    }
}