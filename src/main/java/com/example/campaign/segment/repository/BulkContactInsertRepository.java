package com.example.campaign.segment.repository;

import com.example.campaign.contact.entity.Contact;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BulkContactInsertRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final int CHUNK_SIZE = 500;

    /**
     * Batch INSERT contacts. Returns DB-generated IDs in insertion order.
     * Uses GeneratedKeyHolder — no extra SELECT needed after insert.
     */
    public List<Long> batchInsertContactsAndGetIds(List<Contact> contacts) {
        if (contacts.isEmpty()) return Collections.emptyList();

        String sql = "INSERT INTO contacts (name, phone) VALUES (?, ?)";
        List<Long> allIds = new ArrayList<>();

        List<List<Contact>> chunks = partition(contacts, CHUNK_SIZE);

        for (List<Contact> chunk : chunks) {
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.batchUpdate(
                con -> con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, chunk.get(i).getName());
                        ps.setString(2, chunk.get(i).getPhone());
                    }
                    @Override
                    public int getBatchSize() { return chunk.size(); }
                },
                keyHolder
            );

            // Extract generated IDs — DB-agnostic approach
            keyHolder.getKeyList().stream()
                .map(keyMap -> ((Number) keyMap.values().iterator().next()).longValue())
                .forEach(allIds::add);
        }

        return allIds;
    }

    /**
     * Batch INSERT segment_contacts using pre-captured contact IDs.
     * No SELECT, no JPA — pure JDBC.
     */
    public void batchInsertSegmentContacts(Long segmentId, List<Long> contactIds) {
        if (contactIds.isEmpty()) return;

        String sql = "INSERT INTO segment_contacts (segment_id, contact_id) VALUES (?, ?)";
        List<List<Long>> chunks = partition(contactIds, CHUNK_SIZE);

        for (List<Long> chunk : chunks) {
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, segmentId);
                    ps.setLong(2, chunk.get(i));
                }
                @Override
                public int getBatchSize() { return chunk.size(); }
            });
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
