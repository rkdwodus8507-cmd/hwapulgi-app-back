package com.hwapulgi.api.promotion.dto;

import java.time.LocalDateTime;

public record DailyStatusResponse(boolean claimable, int amount, LocalDateTime lastClaimedAt) {
}
