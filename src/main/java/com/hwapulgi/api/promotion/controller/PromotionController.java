package com.hwapulgi.api.promotion.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.promotion.dto.DailyClaimResponse;
import com.hwapulgi.api.promotion.dto.DailyStatusResponse;
import com.hwapulgi.api.promotion.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Promotion", description = "토스포인트 프로모션")
@RestController
@RequestMapping("/api/v1/promotion")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final AuthService authService;

    @Operation(summary = "일일 출석 포인트 받기")
    @PostMapping("/daily/claim")
    public ApiResponse<DailyClaimResponse> claimDaily(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        return ApiResponse.ok(promotionService.claimDaily(userInfo.userId()));
    }

    @Operation(summary = "일일 출석 수령 가능 여부 조회")
    @GetMapping("/daily/status")
    public ApiResponse<DailyStatusResponse> dailyStatus(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        return ApiResponse.ok(promotionService.getDailyStatus(userInfo.userId()));
    }
}
