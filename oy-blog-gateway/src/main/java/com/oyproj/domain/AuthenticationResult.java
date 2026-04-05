package com.oyproj.domain;

import lombok.Data;

@Data
public class AuthenticationResult {
    private boolean authenticated;
    private String userId;

    public static AuthenticationResult authenticated(String userId) {
        AuthenticationResult result = new AuthenticationResult();
        result.setAuthenticated(true);
        result.setUserId(userId);
        return result;
    }

    public static AuthenticationResult unauthenticated() {
        AuthenticationResult result = new AuthenticationResult();
        result.setAuthenticated(false);
        return result;
    }
}
