package com.example.campaign.whatsapp;


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
