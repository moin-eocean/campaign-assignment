package com.example.campaign.contact.parser;

import com.example.campaign.contact.dto.request.ContactCreateRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExcelContactParser implements ContactFileParser {

    @Override
    public List<ContactCreateRequest> parse(java.io.InputStream inputStream) throws Exception {

        List<ContactCreateRequest> contacts = new ArrayList<>();

        Workbook workbook = new XSSFWorkbook(inputStream);

        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet) {

            if (row.getRowNum() == 0) continue;

            ContactCreateRequest req = new ContactCreateRequest();

            req.setName(row.getCell(0).getStringCellValue());
            req.setPhone(row.getCell(1).getStringCellValue());

            contacts.add(req);
        }

        return contacts;
    }
}