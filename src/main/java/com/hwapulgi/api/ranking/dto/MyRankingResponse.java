package com.hwapulgi.api.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyRankingResponse {
    private final int rank;
    private final long totalParticipants;
    private final double score;
}
