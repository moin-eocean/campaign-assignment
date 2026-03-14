package com.example.campaign.common.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class ValidationFailedException extends RuntimeException {


    public ValidationFailedException(String message) {
        super(message);
    }

}