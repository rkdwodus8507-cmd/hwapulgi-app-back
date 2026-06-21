package com.hwapulgi.api.auth.service;

import com.hwapulgi.api.auth.client.AppsInTossClient;
import com.hwapulgi.api.auth.crypto.TossDataDecryptor;
import com.hwapulgi.api.auth.dto.TossGenerateTokenResponse;
import com.hwapulgi.api.auth.dto.TossGenerateTokenResponse.TokenSuccess;
import com.hwapulgi.api.auth.dto.TossLoginMeResponse;
import com.hwapulgi.api.auth.dto.TossLoginMeResponse.LoginMeSuccess;
import com.hwapulgi.api.auth.dto.TossLoginRequest;
import com.hwapulgi.api.auth.jwt.JwtTokenProvider;
import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TossAuthServiceTest {

    @Mock private AppsInTossClient appsInTossClient;
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TossDataDecryptor tossDataDecryptor;

    private TossAuthService service;

    @BeforeEach
    void setUp() {
        service = new TossAuthService(appsInTossClient, userRepository, jwtTokenProvider, tossDataDecryptor);
        ReflectionTestUtils.setField(service, "accessExpiry", 3600L);

        given(jwtTokenProvider.createAccessToken(any(), any())).willReturn("ACCESS");
        given(jwtTokenProvider.createRefreshToken(any())).willReturn("REFRESH");
    }

    private void givenTossLoginOk(String encryptedName) {
        given(appsInTossClient.generateToken(any(), any()))
                .willReturn(new TossGenerateTokenResponse("SUCCESS",
                        new TokenSuccess("toss-access", "toss-refresh", "Bearer", 3600, "scope"), null));
        LoginMeSuccess me = new LoginMeSuccess(
                12345L, "scope", List.of(), encryptedName,
                null, null, null, null, null, null, null);
        given(appsInTossClient.getLoginMe("toss-access"))
                .willReturn(new TossLoginMeResponse("SUCCESS", me, null));
        given(userRepository.findByExternalId("12345")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void login_newUser_usesDecryptedNameAsNickname() {
        givenTossLoginOk("ENC_NAME");
        given(tossDataDecryptor.decrypt("ENC_NAME")).willReturn("홍길동");

        service.login(new TossLoginRequest("auth-code", "ref"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("홍길동");
        assertThat(captor.getValue().getExternalId()).isEqualTo("12345");
    }

    @Test
    void login_decryptionFails_fallsBackToDefaultNickname() {
        givenTossLoginOk("ENC_NAME");
        given(tossDataDecryptor.decrypt("ENC_NAME"))
                .willThrow(new BusinessException(ErrorCode.INTERNAL_ERROR));

        service.login(new TossLoginRequest("auth-code", "ref"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("토스유저12345");
    }

    @Test
    void login_noName_fallsBackToDefaultNickname() {
        givenTossLoginOk(null);
        given(tossDataDecryptor.decrypt(null)).willReturn(null);

        service.login(new TossLoginRequest("auth-code", "ref"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("토스유저12345");
    }
}
