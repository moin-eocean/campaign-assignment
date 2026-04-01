package com.example.campaign.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class CampaignExecutorPoolConfig {

    @Bean("campaignExecutorPool")
    public ExecutorService campaignExecutorPool() {
        return new ThreadPoolExecutor(
                0,                        // core threads — 0 so idle threads get destroyed
                10,                       // max 10 concurrent campaigns
                60L, TimeUnit.SECONDS,    // idle thread destroyed after 60s
                new SynchronousQueue<>()  // no queue — if no thread available, reject immediately
        );
    }
}
