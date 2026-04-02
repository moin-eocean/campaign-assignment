package com.example.campaign.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Configuration
public class CampaignExecutorPoolConfig {

    @Bean("campaignSemaphore")
    public Semaphore campaignSemaphore() {
        return new Semaphore(10);
    }

    @Bean("campaignExecutorPool")
    public ExecutorService campaignExecutorPool() {
        return Executors.newCachedThreadPool();
    }
}
