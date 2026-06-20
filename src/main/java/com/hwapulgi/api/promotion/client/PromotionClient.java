package com.hwapulgi.api.promotion.client;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.promotion.dto.PromotionExecuteRequest;
import com.hwapulgi.api.promotion.dto.PromotionExecuteResponse;
import com.hwapulgi.api.promotion.dto.PromotionKeyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class PromotionClient {

    private static final String GET_KEY_PATH = "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key";
    private static final String EXECUTE_PATH = "/api-partner/v1/apps-in-toss/promotion/execute-promotion";
    private static final String USER_KEY_HEADER = "x-toss-user-key";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PromotionClient(
            @Qualifier("appsInTossRestTemplate") RestTemplate restTemplate,
            @Qualifier("appsInTossBaseUrl") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public String generateKey(String userKey) {
        HttpHeaders headers = baseHeaders(userKey);
        try {
            ResponseEntity<PromotionKeyResponse> response = restTemplate.exchange(
                    baseUrl + GET_KEY_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    PromotionKeyResponse.class
            );
            PromotionKeyResponse body = response.getBody();
            if (body == null || !body.isSuccess()) {
                log.error("프로모션 키 발급 실패: {}", body);
                throw new BusinessException(ErrorCode.PROMOTION_FAILED);
            }
            return body.success().key();
        } catch (RestClientException e) {
            log.error("프로모션 키 발급 통신 오류", e);
            throw new BusinessException(ErrorCode.PROMOTION_FAILED);
        }
    }

    public void executePromotion(String userKey, String promotionCode, String key, int amount) {
        HttpHeaders headers = baseHeaders(userKey);
        PromotionExecuteRequest request = new PromotionExecuteRequest(promotionCode, key, amount);
        try {
            ResponseEntity<PromotionExecuteResponse> response = restTemplate.exchange(
                    baseUrl + EXECUTE_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    PromotionExecuteResponse.class
            );
            PromotionExecuteResponse body = response.getBody();
            if (body == null || !body.isSuccess()) {
                log.error("프로모션 적립 실패: {}", body);
                throw new BusinessException(ErrorCode.PROMOTION_FAILED);
            }
        } catch (RestClientException e) {
            log.error("프로모션 적립 통신 오류", e);
            throw new BusinessException(ErrorCode.PROMOTION_FAILED);
        }
    }

    private HttpHeaders baseHeaders(String userKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(USER_KEY_HEADER, userKey);
        return headers;
    }
}
