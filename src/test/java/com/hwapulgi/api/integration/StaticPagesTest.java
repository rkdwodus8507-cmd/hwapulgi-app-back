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

    private String bodyOf(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
