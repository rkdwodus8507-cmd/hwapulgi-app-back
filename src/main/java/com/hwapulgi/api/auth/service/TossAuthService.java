package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.client.AppsInTossClient;
import com.hwapulgi.api.auth.crypto.TossDataDecryptor;
import com.hwapulgi.api.auth.dto.*;
import com.hwapulgi.api.auth.dto.TossLoginMeResponse.LoginMeSuccess;
import com.hwapulgi.api.auth.jwt.JwtPayload;
import com.hwapulgi.api.auth.jwt.JwtRefreshPayload;
import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TossAuthService {

    private final AppsInTossClient appsInTossClient;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TossDataDecryptor tossDataDecryptor;

    @Value("${jwt.access-token-expiry-seconds}")
    private long accessExpiry;

    @Transactional
    public TossLoginResponse login(TossLoginRequest request) {
        // 1. 인가코드로 토스 AccessToken 발급
        TossGenerateTokenResponse tokenResponse = appsInTossClient.generateToken(
                request.authorizationCode(), request.referrer()
        );

        if (!tokenResponse.isSuccess()) {
            log.error("토스 토큰 발급 실패: error={}", tokenResponse.error());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String tossAccessToken = tokenResponse.success().accessToken();

        // 2. 토스 AccessToken으로 사용자 정보 조회 (외부 노출 X)
        TossLoginMeResponse loginMeResponse = appsInTossClient.getLoginMe(tossAccessToken);

        if (!loginMeResponse.isSuccess()) {
            log.error("토스 사용자 정보 조회 실패: error={}", loginMeResponse.error());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LoginMeSuccess me = loginMeResponse.success();
        Long tossUserKey = me.userKey();
        String externalId = String.valueOf(tossUserKey);

        // 3. 기존 유저 조회 or 신규 생성 (신규 시 복호화한 이름을 닉네임으로 사용)
        User user = userRepository.findByExternalId(externalId)
                .orElseGet(() -> userRepository.save(
                        User.tossUser(externalId, resolveNickname(me, tossUserKey))
                ));

        // 4. 자체 JWT 발급 (토스 access_token은 외부 노출하지 않음)
        return new TossLoginResponse(
                user.getId(),
                user.getNickname(),
                jwtTokenProvider.createAccessToken(user.getId(), user.getNickname()),
                jwtTokenProvider.createRefreshToken(user.getId()),
                accessExpiry
        );
    }

    /**
     * 신규 가입 시 사용할 닉네임을 결정한다.
     * 토스가 내려준 암호화된 이름을 복호화해 사용하고, 복호화 키 미설정·미동의·실패 시에는
     * "토스유저{userKey}"로 폴백한다(로그인 자체는 이름 복호화 실패와 무관하게 성공해야 함).
     */
    private String resolveNickname(LoginMeSuccess me, Long userKey) {
        try {
            String name = tossDataDecryptor.decrypt(me.name());
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Exception e) {
            log.warn("토스 사용자 이름 복호화 실패, 기본 닉네임 사용: userKey={}", userKey, e);
        }
        return "토스유저" + userKey;
    }

    public TossLoginResponse refresh(String refreshToken) {
        JwtRefreshPayload payload = jwtTokenProvider.parseRefreshToken(refreshToken);
        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new TossLoginResponse(
                user.getId(),
                user.getNickname(),
                jwtTokenProvider.createAccessToken(user.getId(), user.getNickname()),
                jwtTokenProvider.createRefreshToken(user.getId()),
                accessExpiry
        );
    }

    @Transactional
    public void unlink(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String token = authorizationHeader.substring(7).trim();
        JwtPayload payload = jwtTokenProvider.parseAccessToken(token);
        userRepository.findById(payload.userId()).ifPresent(userRepository::delete);
    }

    @Transactional
    public void handleUnlinkCallback(Long userKey, String referrer) {
        String externalId = String.valueOf(userKey);
        log.info("토스 연결 해제 콜백 수신: userKey={}, referrer={}", userKey, referrer);

        userRepository.findByExternalId(externalId).ifPresent(user -> {
            log.info("유저 연결 해제 처리: userId={}, externalId={}", user.getId(), externalId);
            userRepository.delete(user);
        });
    }
}
