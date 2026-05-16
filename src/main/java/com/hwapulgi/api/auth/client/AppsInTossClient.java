package com.hwapulgi.api.auth.client;

import com.hwapulgi.api.auth.dto.TossGenerateTokenRequest;
import com.hwapulgi.api.auth.dto.TossGenerateTokenResponse;
import com.hwapulgi.api.auth.dto.TossLoginMeResponse;
import com.hwapulgi.api.auth.dto.TossRefreshTokenRequest;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class AppsInTossClient {

    private static final String TOKEN_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/generate-token";
    private static final String REFRESH_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/refresh-token";
    private static final String LOGIN_ME_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/login-me";
    private static final String REMOVE_BY_TOKEN_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/access/remove-by-access-token";
    private static final String REMOVE_BY_USER_KEY_PATH = "/api-partner/v1/apps-in-toss/user/oauth2/access/remove-by-user-key";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AppsInTossClient(
            @Qualifier("appsInTossRestTemplate") RestTemplate restTemplate,
            @Qualifier("appsInTossBaseUrl") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public TossGenerateTokenResponse generateToken(String authorizationCode, String referrer) {
        TossGenerateTokenRequest request = new TossGenerateTokenRequest(authorizationCode, referrer);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<TossGenerateTokenResponse> response = restTemplate.exchange(
                    baseUrl + TOKEN_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    TossGenerateTokenResponse.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("앱인토스 토큰 발급 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public TossGenerateTokenResponse refreshToken(String refreshToken) {
        TossRefreshTokenRequest request = new TossRefreshTokenRequest(refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<TossGenerateTokenResponse> response = restTemplate.exchange(
                    baseUrl + REFRESH_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    TossGenerateTokenResponse.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("앱인토스 토큰 갱신 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public TossLoginMeResponse getLoginMe(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<TossLoginMeResponse> response = restTemplate.exchange(
                    baseUrl + LOGIN_ME_PATH,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    TossLoginMeResponse.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            log.error("앱인토스 사용자 정보 조회 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public void removeByAccessToken(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            restTemplate.exchange(
                    baseUrl + REMOVE_BY_TOKEN_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    String.class
            );
        } catch (RestClientException e) {
            log.error("앱인토스 연결 해제 실패 (accessToken)", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public void removeByUserKey(String accessToken, Long userKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.exchange(
                    baseUrl + REMOVE_BY_USER_KEY_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(java.util.Map.of("userKey", userKey), headers),
                    String.class
            );
        } catch (RestClientException e) {
            log.error("앱인토스 연결 해제 실패 (userKey={})", userKey, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
