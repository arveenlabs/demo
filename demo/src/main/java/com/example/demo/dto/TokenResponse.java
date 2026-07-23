package com.example.demo.dto;

public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "bearer";

    public TokenResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
}
