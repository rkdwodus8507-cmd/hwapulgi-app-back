package com.hwapulgi.api.auth.crypto;

import com.hwapulgi.api.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토스 규격(AES-256-GCM, [IV(12)][ciphertext][tag(16)] Base64, AAD)대로 암호화한 값을
 * TossDataDecryptor가 복호화하는지 검증한다. 실제 토스 키 대신 테스트에서 생성한 키를 사용한다.
 */
class TossDataDecryptorTest {

    private static final String AAD = "TOSS";

    /** 토스가 보내는 것과 동일한 포맷으로 평문을 암호화한다. */
    private String encryptLikeToss(String plaintext, byte[] keyBytes, String aad) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        byte[] cipherAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherAndTag.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherAndTag, 0, combined, iv.length, cipherAndTag.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private byte[] newAes256Key() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        SecretKey key = kg.generateKey();
        return key.getEncoded();
    }

    @Test
    void decrypt_roundTrip_returnsPlaintext() throws Exception {
        byte[] key = newAes256Key();
        String base64Key = Base64.getEncoder().encodeToString(key);
        TossDataDecryptor decryptor = new TossDataDecryptor(base64Key, AAD);

        String encrypted = encryptLikeToss("홍길동", key, AAD);

        assertThat(decryptor.isEnabled()).isTrue();
        assertThat(decryptor.decrypt(encrypted)).isEqualTo("홍길동");
    }

    @Test
    void decrypt_nullOrBlank_returnsNull() throws Exception {
        String base64Key = Base64.getEncoder().encodeToString(newAes256Key());
        TossDataDecryptor decryptor = new TossDataDecryptor(base64Key, AAD);

        assertThat(decryptor.decrypt(null)).isNull();
        assertThat(decryptor.decrypt("")).isNull();
        assertThat(decryptor.decrypt("   ")).isNull();
    }

    @Test
    void decrypt_wrongAad_throws() throws Exception {
        byte[] key = newAes256Key();
        TossDataDecryptor decryptor =
                new TossDataDecryptor(Base64.getEncoder().encodeToString(key), AAD);

        String encryptedWithWrongAad = encryptLikeToss("홍길동", key, "WRONG");

        assertThatThrownBy(() -> decryptor.decrypt(encryptedWithWrongAad))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void decrypt_wrongKey_throws() throws Exception {
        byte[] encryptKey = newAes256Key();
        byte[] otherKey = newAes256Key();
        TossDataDecryptor decryptor =
                new TossDataDecryptor(Base64.getEncoder().encodeToString(otherKey), AAD);

        String encrypted = encryptLikeToss("홍길동", encryptKey, AAD);

        assertThatThrownBy(() -> decryptor.decrypt(encrypted))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void notConfigured_isDisabled_andDecryptThrows() {
        TossDataDecryptor decryptor = new TossDataDecryptor("", AAD);

        assertThat(decryptor.isEnabled()).isFalse();
        assertThat(decryptor.decrypt(null)).isNull(); // null 입력은 그대로 null
        assertThatThrownBy(() -> decryptor.decrypt("anything"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidKeyLength_failsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // 128bit
        assertThatThrownBy(() -> new TossDataDecryptor(shortKey, AAD))
                .isInstanceOf(IllegalStateException.class);
    }
}
