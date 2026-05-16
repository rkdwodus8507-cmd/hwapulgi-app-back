package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.client.AppsInTossClient;
import com.hwapulgi.api.auth.dto.*;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TossAuthService {

    private final AppsInTossClient appsInTossClient;
    private final UserRepository userRepository;

    @Transactional
    public TossLoginResponse login(TossLoginRequest request) {
        // 1. 인가코드로 AccessToken 발급
        TossGenerateTokenResponse tokenResponse = appsInTossClient.generateToken(
                request.authorizationCode(), request.referrer()
        );

        if (!tokenResponse.isSuccess()) {
            log.error("토스 토큰 발급 실패: error={}", tokenResponse.error());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = tokenResponse.success().accessToken();
        String refreshToken = tokenResponse.success().refreshToken();

        // 2. AccessToken으로 사용자 정보 조회
        TossLoginMeResponse loginMeResponse = appsInTossClient.getLoginMe(accessToken);

        if (!loginMeResponse.isSuccess()) {
            log.error("토스 사용자 정보 조회 실패: error={}", loginMeResponse.error());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // TODO: Client ID 발급 후 개인정보 복호화 로직 추가 (AES-256-GCM)
        //  - loginMeResponse.success().name() 등 암호화된 필드 복호화
        //  - 복호화 키는 토스 로그인 신청 시 발급됨
        Long tossUserKey = loginMeResponse.success().userKey();
        String externalId = String.valueOf(tossUserKey);

        // 3. 기존 유저 조회 or 신규 생성
        User user = userRepository.findByExternalId(externalId)
                .orElseGet(() -> userRepository.save(
                        new User(externalId, "토스유저" + tossUserKey)
                ));

        return new TossLoginResponse(
                user.getId(),
                user.getNickname(),
                accessToken,
                refreshToken,
                tokenResponse.success().expiresIn()
        );
    }

    public TossLoginResponse refresh(String refreshToken) {
        TossGenerateTokenResponse tokenResponse = appsInTossClient.refreshToken(refreshToken);

        if (!tokenResponse.isSuccess()) {
            log.error("토스 토큰 갱신 실패: error={}", tokenResponse.error());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String newAccessToken = tokenResponse.success().accessToken();

        TossLoginMeResponse loginMeResponse = appsInTossClient.getLoginMe(newAccessToken);
        if (!loginMeResponse.isSuccess()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String externalId = String.valueOf(loginMeResponse.success().userKey());
        User user = userRepository.findByExternalId(externalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new TossLoginResponse(
                user.getId(),
                user.getNickname(),
                newAccessToken,
                tokenResponse.success().refreshToken(),
                tokenResponse.success().expiresIn()
        );
    }

    public void unlink(String accessToken) {
        appsInTossClient.removeByAccessToken(accessToken);
    }

    @Transactional
    public void handleUnlinkCallback(Long userKey, String referrer) {
        String externalId = String.valueOf(userKey);
        log.info("토스 연결 해제 콜백 수신: userKey={}, referrer={}", userKey, referrer);

        userRepository.findByExternalId(externalId).ifPresent(user -> {
            log.info("유저 연결 해제 처리: userId={}, externalId={}", user.getId(), externalId);
            // TODO: 유저 데이터 정리 정책 결정 (삭제 vs 비활성화)
        });
    }
}
