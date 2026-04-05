package com.hwapulgi.api.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RankingEntryResponse {
    private final int rank;
    private final Long userId;
    private final String nickname;
    private final double score;
}
