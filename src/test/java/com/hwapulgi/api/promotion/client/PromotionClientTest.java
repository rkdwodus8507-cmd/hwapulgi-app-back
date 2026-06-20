package com.hwapulgi.api.promotion.client;

import com.hwapulgi.api.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PromotionClientTest {

    private static final String BASE = "https://apps-in-toss-api.toss.im";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private PromotionClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new PromotionClient(restTemplate, BASE);
    }

    @Test
    void generateKey_success_returnsKey() {
        server.expect(requestTo(BASE + "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-toss-user-key", "12345"))
                .andRespond(withSuccess(
                        "{\"resultType\":\"SUCCESS\",\"success\":{\"key\":\"KEY_ABC\"},\"error\":null}",
                        MediaType.APPLICATION_JSON));

        String key = client.generateKey("12345");

        assertThat(key).isEqualTo("KEY_ABC");
        server.verify();
    }

    @Test
    void generateKey_failure_throwsBusinessException() {
        server.expect(requestTo(BASE + "/api-partner/v1/apps-in-toss/promotion/execute-promotion/get-key"))
                .andRespond(withSuccess(
                        "{\"resultType\":\"FAIL\",\"success\":null,\"error\":\"some-error\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateKey("12345"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void executePromotion_success() {
        server.expect(requestTo(BASE + "/api-partner/v1/apps-in-toss/promotion/execute-promotion"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-toss-user-key", "12345"))
                .andExpect(jsonPath("$.promotionCode").value("DAILY"))
                .andExpect(jsonPath("$.key").value("KEY_ABC"))
                .andExpect(jsonPath("$.amount").value(100))
                .andRespond(withSuccess(
                        "{\"resultType\":\"SUCCESS\",\"error\":null}",
                        MediaType.APPLICATION_JSON));

        client.executePromotion("12345", "DAILY", "KEY_ABC", 100);

        server.verify();
    }
}
