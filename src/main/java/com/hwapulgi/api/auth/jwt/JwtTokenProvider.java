package com.hwapulgi.api.auth.jwt;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpirySeconds;
    private final long refreshExpirySeconds;
    private final Clock clock;

    /** Spring-injected constructor */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-seconds}") long accessExpirySeconds,
            @Value("${jwt.refresh-token-expiry-seconds}") long refreshExpirySeconds) {
        this(secret, accessExpirySeconds, refreshExpirySeconds, Clock.systemUTC());
    }

    /** Visible for tests (clock injection) */
    public JwtTokenProvider(String secret, long accessExpirySeconds, long refreshExpirySeconds, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirySeconds = accessExpirySeconds;
        this.refreshExpirySeconds = refreshExpirySeconds;
        this.clock = clock;
    }

    public String createAccessToken(Long userId, String nickname) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("nickname", nickname)
                .claim("type", "access")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(accessExpirySeconds)))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(refreshExpirySeconds)))
                .signWith(key)
                .compact();
    }

    public JwtPayload parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"access".equals(claims.get("type", String.class))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }

            return new JwtPayload(
                    Long.parseLong(claims.getSubject()),
                    claims.get("nickname", String.class)
            );
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public JwtRefreshPayload parseRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }

            return new JwtRefreshPayload(Long.parseLong(claims.getSubject()));
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
