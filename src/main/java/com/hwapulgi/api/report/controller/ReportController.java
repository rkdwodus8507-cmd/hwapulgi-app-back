package com.hwapulgi.api.report.controller;

import com.hwapulgi.api.auth.dto.UserInfo;
import com.hwapulgi.api.auth.service.AuthService;
import com.hwapulgi.api.common.response.ApiResponse;
import com.hwapulgi.api.report.dto.WeeklySummaryResponse;
import com.hwapulgi.api.report.service.ReportService;
import com.hwapulgi.api.user.entity.User;
import com.hwapulgi.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Report", description = "주간 리포트")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "이번 주 리포트", description = "캘린더, 대상 TOP3, 가장 힘든 요일, 주간 헤드라인 등 상세 리포트")
    @GetMapping("/weekly")
    public ApiResponse<WeeklySummaryResponse> getWeeklySummary(
            @RequestHeader(value = "Authorization", defaultValue = "") String token) {
        UserInfo userInfo = authService.authenticate(token);
        User user = userService.getOrCreateUser(userInfo.getUserId(), userInfo.getNickname());
        return ApiResponse.ok(reportService.getWeeklySummary(user.getId()));
    }
}
