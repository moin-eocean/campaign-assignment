package com.example.campaign.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;

/**
 * Exposes a single, shared {@link Semaphore} that limits the total number of
 * concurrent WhatsApp API calls across ALL running campaigns.
 * <p>
 * WhatsApp Business API enforces throughput limits at the portfolio level,
 * so this semaphore prevents exceeding those limits regardless of how many
 * campaigns are executing simultaneously.
 */
@Configuration
public class GlobalSemaphoreConfig {

    @Value("${whatsapp.semaphore.permits:80}")
    private int permits;

    @Bean(name = "whatsappGlobalSemaphore")
    public Semaphore whatsappGlobalSemaphore() {
        return new Semaphore(permits, true); // fair = true → prevents starvation
    }
}
