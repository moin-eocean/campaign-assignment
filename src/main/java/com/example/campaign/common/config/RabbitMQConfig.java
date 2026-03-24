package com.example.campaign.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {


    public static final String CAMPAIGN_QUEUE = "campaign.queue";
    public static final String CAMPAIGN_EXCHANGE = "campaign.exchange";
    public static final String CAMPAIGN_ROUTING_KEY = "campaign.routing.key";


    @Bean
    public Queue campaignQueue() {
        return QueueBuilder
                .durable(CAMPAIGN_QUEUE)
                .build();
    }

    @Bean
    public DirectExchange campaignExchange() {
        return new DirectExchange(CAMPAIGN_EXCHANGE);
    }

    @Bean
    public Binding campaignBinding(Queue campaignQueue, DirectExchange campaignExchange) {
        return BindingBuilder
                .bind(campaignQueue)
                .to(campaignExchange)
                .with(CAMPAIGN_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}