package com.hwapulgi.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토스 개발자 포털에 등록하는 약관/개인정보처리방침 정적 페이지가
 * 인증 없이 공개 서빙되는지 검증한다. (정적 리소스가 인증/필터에 막히면 등록 URL이 깨진다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaticPagesTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void termsPage_isPubliclyServed() throws Exception {
        assertThat(bodyOf("/terms.html")).contains("이용약관");
    }

    @Test
    void privacyPage_isPubliclyServed() throws Exception {
        assertThat(bodyOf("/privacy.html")).contains("개인정보처리방침");
    }

    @Test
    void gameIndexHtml_isDeployedAndServed() throws Exception {
        // 등급분류 심의용 브라우저 게임의 진입 파일이 static에 배포되어 직접 서빙되는지 (실제 내용 검증)
        MvcResult result = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains("<div id=\"root\">");
        assertThat(body).contains("/assets/index-");
    }

    @Test
    void root_forwardsToIndexHtml() throws Exception {
        MvcResult result = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getForwardedUrl()).contains("index.html");
    }

    @Test
    void spaRoutes_forwardToIndexHtml() throws Exception {
        for (String path : new String[] {"/home", "/play", "/start/target", "/result"}) {
            MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getForwardedUrl())
                    .as("SPA 경로 %s 는 index.html로 포워딩되어야 함", path)
                    .contains("index.html");
        }
    }

    private String bodyOf(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
