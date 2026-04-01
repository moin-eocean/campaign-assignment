package com.example.campaign.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Simulates WhatsApp Business API for local development.
 * <p>
 * Introduces realistic network delays (1500–3500ms) and an 85% success rate
 * to mirror production-like behavior during testing.
 */
@Slf4j
@Service
@Primary
public class DummyWhatsAppApiClient implements WhatsAppApiClient {

    private static final Random RANDOM = new Random();

    private static final List<String> FAILURE_REASONS = List.of(
            "INVALID_NUMBER",
            "USER_BLOCKED",
            "RATE_LIMIT_EXCEEDED",
            "TEMPLATE_NOT_APPROVED"
    );

    @Override
    public SendResult send(String phoneNumber, String messageJson) {
        long delayMs = 1200;
        log.debug("[DummyWhatsApp] Sending to {} — simulated delay {}ms", phoneNumber, delayMs);

        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.fail("INTERRUPTED", e.getMessage());
        }

        boolean success = RANDOM.nextInt(100) < 85; // 85% success rate

        if (success) {
            log.debug("[DummyWhatsApp] SUCCESS for {}", phoneNumber);
            return SendResult.ok();
        } else {
            String reason = FAILURE_REASONS.get(RANDOM.nextInt(FAILURE_REASONS.size()));
            log.debug("[DummyWhatsApp] FAILED for {} — reason: {}", phoneNumber, reason);
            return SendResult.fail(reason, "Simulated failure by DummyWhatsAppApiClient");
        }
    }
}
