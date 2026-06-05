package com.hwapulgi.api.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String authHeader = "Authorization";

        return new OpenAPI()
                .info(new Info()
                        .title("Hwapulgi API")
                        .description("화풀기 — 분노 해소 게임 앱 백엔드 API\n\n"
                                + "인증: Authorization 헤더에 `Bearer <JWT>` 형식으로 전달.\n"
                                + "JWT는 `POST /api/auth/guest/login` 또는 토스 로그인으로 발급.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(authHeader))
                .components(new Components()
                        .addSecuritySchemes(authHeader, new SecurityScheme()
                                .name(authHeader)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Bearer <JWT> (POST /api/auth/guest/login 으로 발급)")));
    }
}
