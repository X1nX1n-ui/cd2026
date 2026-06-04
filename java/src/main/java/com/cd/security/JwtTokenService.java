package com.cd.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.expiration-seconds:43200}") long expirationSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(Long userId, String userName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userName)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Object value = parseClaims(token).get("uid");
        if (value instanceof Integer intValue) {
            return intValue.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
