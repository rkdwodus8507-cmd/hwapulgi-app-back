package com.hwapulgi.api.promotion.dto;

import java.time.LocalDateTime;

public record DailyClaimResponse(boolean granted, int amount, LocalDateTime grantedAt) {
}
