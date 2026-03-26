package com.example.campaign.whatsapp;

/**
 * Abstraction for sending messages via WhatsApp Business API.
 * <p>
 * Implementations should be thread-safe — multiple virtual threads
 * will invoke {@link #send} concurrently.
 */
public interface WhatsAppApiClient {

    /**
     * Send a WhatsApp message to the given phone number.
     *
     * @param phoneNumber E.164 format, e.g. "+923001234567"
     * @param messageJson raw JSON string of the message payload
     * @return {@link SendResult} indicating success or failure with reason
     */
    SendResult send(String phoneNumber, String messageJson);
}
