package com.hwapulgi.api.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * 로컬/테스트 환경에서 mTLS 인증서 없이 AppsInToss 빈을 제공합니다.
 * dev/prod에서는 {@link AppsInTossConfig}(@Profile("!local"))가 실제 mTLS RestTemplate을 구성합니다.
 * 통합 테스트에서 외부 호출 클라이언트는 @MockBean으로 대체되므로 이 RestTemplate은 실제로 사용되지 않습니다.
 */
@Configuration
@Profile("local")
public class LocalAppsInTossConfig {

    @Value("${appintoss.api.base-url}")
    private String baseUrl;

    @Bean
    public RestTemplate appsInTossRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public String appsInTossBaseUrl() {
        return baseUrl;
    }
}
