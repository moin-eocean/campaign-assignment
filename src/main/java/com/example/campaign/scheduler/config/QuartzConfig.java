package com.example.campaign.scheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz ko Spring context se aware karta hai
 * taaki Jobs mein @Autowired kaam kare.
 */
@Configuration
public class QuartzConfig {

    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext) {
        SpringBeanJobFactory factory = new SpringBeanJobFactory();
        factory.setApplicationContext(applicationContext);
        return factory;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
