package com.hwapulgi.api.auth.crypto;

import com.hwapulgi.api.common.exception.BusinessException;
import com.hwapulgi.api.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * 토스 로그인(login-me) 응답의 개인정보 필드를 복호화한다.
 * 앱인토스 규격: AES-256-GCM, 암호문은 [IV(12B)][ciphertext][authTag(16B)]를
 * Base64(standard)로 인코딩한 형태. 복호화 키도 Base64 인코딩된 256bit 키이며,
 * AAD(Additional Authenticated Data)는 복호화 키와 함께 전달된다(기본값 "TOSS").
 *
 * 키는 비밀값이므로 환경변수(APPINTOSS_LOGIN_DECRYPT_KEY)로 주입한다.
 */
@Slf4j
@Component
public class TossDataDecryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final byte[] keyBytes;
    private final byte[] aadBytes;
    private final boolean enabled;

    public TossDataDecryptor(
            @Value("${appintoss.login.decrypt-key:}") String base64Key,
            @Value("${appintoss.login.aad:TOSS}") String aad) {
        this.enabled = base64Key != null && !base64Key.isBlank();
        this.keyBytes = enabled ? Base64.getDecoder().decode(base64Key.trim()) : null;
        this.aadBytes = aad.getBytes(StandardCharsets.UTF_8);
        if (enabled && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "토스 복호화 키는 Base64 디코딩 시 32바이트(256bit)여야 합니다. 실제: " + keyBytes.length);
        }
        if (!enabled) {
            log.warn("토스 로그인 복호화 키가 설정되지 않았습니다(appintoss.login.decrypt-key). "
                    + "개인정보 복호화가 비활성화됩니다.");
        }
    }

    /** 복호화 키가 설정되어 사용 가능한지 여부. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 암호문(Base64)을 평문으로 복호화한다.
     * 입력이 null/blank이면 null을 반환한다(해당 scope 미동의 등으로 필드가 비어 올 수 있음).
     *
     * @throws IllegalStateException 복호화 키가 설정되지 않은 경우
     * @throws BusinessException     복호화 실패(키/AAD 불일치, 위변조 등)
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) {
            return null;
        }
        if (!enabled) {
            throw new IllegalStateException(
                    "토스 복호화 키가 설정되지 않았습니다(appintoss.login.decrypt-key).");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aadBytes);

            byte[] plain = cipher.doFinal(decoded, IV_LENGTH, decoded.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("토스 개인정보 복호화 실패", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
