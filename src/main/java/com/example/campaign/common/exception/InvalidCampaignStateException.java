package com.example.campaign.common.exception;

/**
 * Thrown when a campaign state transition is invalid.
 * E.g., trying to pause a non-RUNNING campaign or resume a STOPPED campaign.
 */
public class InvalidCampaignStateException extends RuntimeException {

    public InvalidCampaignStateException(String message) {
        super(message);
    }
}
