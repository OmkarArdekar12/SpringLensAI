package com.springlensai.server.service.ai;

import java.util.Locale;

public final class AiErrors {

    private AiErrors() {
    }

    public static boolean isRateLimited(Throwable error) {
        return chainContains(error, "429", "resource_exhausted", "quota", "rate limit", "too many requests");
    }

    public static boolean isTransient(Throwable error) {
        return isRateLimited(error) || chainContains(error, "503", "unavailable", "504", "deadline", "timed out", "timeout", "overloaded");
    }

    public static String userMessage(Throwable error) {
        if(isRateLimited(error)) {
            return "The AI service is rate-limited right now. Please wait a minute and try again.";
        }
        if(isTransient(error)) {
            return "The AI service is temporarily unavailable. Please try again shortly.";
        }
        return "The AI service could not complete the request. Please try again.";
    }

    private static boolean chainContains(Throwable error, String... needles) {
        int depth = 0;
        for(Throwable t = error; t != null && depth < 8; t = t.getCause(), depth++) {
            String message = t.getMessage();
            if(message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            for(String needle : needles) {
                if(lower.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
