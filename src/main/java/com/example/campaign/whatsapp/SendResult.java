package com.example.campaign.whatsapp;

/**
 * Value object representing the outcome of a WhatsApp API send call.
 *
 * @param success       true if the message was sent successfully
 * @param failureReason categorized failure reason (null if success)
 * @param rawError      raw exception or API error body (null if success)
 */
public record SendResult(
        boolean success,
        String failureReason,
        String rawError
) {

    public static SendResult ok() {
        return new SendResult(true, null, null);
    }

    public static SendResult fail(String reason, String rawError) {
        return new SendResult(false, reason, rawError);
    }
}
