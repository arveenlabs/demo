package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final long ACCESS_TTL_MINUTES = 60L * 24;   // 1 day
    private static final long REFRESH_TTL_DAYS   = 30L;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(String userId, String type, Duration ttl, String jti) {
        return Jwts.builder()
                .subject(userId)
                .claim("type", type)
                .id(jti)
                .expiration(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .signWith(key())
                .compact();
    }

    public String createAccessToken(String userId, String jti) {
        return createToken(userId, "access", Duration.ofMinutes(ACCESS_TTL_MINUTES), jti);
    }

    public String createRefreshToken(String userId, String jti) {
        return createToken(userId, "refresh", Duration.ofDays(REFRESH_TTL_DAYS), jti);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Returns null if invalid / wrong type. */
    public String extractUserIdFromAccessToken(String token) {
        try {
            Claims c = parseClaims(token);
            if (!"access".equals(c.get("type", String.class))) return null;
            return c.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
