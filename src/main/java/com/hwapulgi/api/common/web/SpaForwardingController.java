package com.hwapulgi.api.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA(React Router) 클라이언트 라우트를 새로고침/딥링크로 직접 접근할 때 index.html을 반환한다.
 * (게임물 등급분류 심의용 브라우저 데모 빌드를 백엔드 도메인에서 서빙하기 위함)
 *
 * 루트("/")는 Spring Boot가 정적 index.html을 자동 서빙하므로 매핑하지 않는다.
 * API(/api/**)와 정적 파일(terms.html, privacy.html, /assets/** 등)은 영향받지 않는다.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({
            "/intro",
            "/start/target",
            "/start/name",
            "/start/anger",
            "/play",
            "/result",
            "/home",
            "/reports",
            "/ranking"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
