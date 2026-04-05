package com.hwapulgi.api.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."),
    POINTS_MISMATCH(HttpStatus.BAD_REQUEST, "POINTS_MISMATCH", "포인트 검증에 실패했습니다."),
    RANKING_NOT_FOUND(HttpStatus.NOT_FOUND, "RANKING_NOT_FOUND", "랭킹 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}