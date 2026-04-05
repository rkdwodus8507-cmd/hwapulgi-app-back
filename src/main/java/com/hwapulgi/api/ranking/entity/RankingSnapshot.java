package com.hwapulgi.api.ranking.entity;

import com.hwapulgi.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ranking_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodType periodType;

    @Column(nullable = false)
    private String periodKey;

    private int totalPoints;
    private int totalSessions;
    private int totalHits;
    private int bestRelease;
    private int avgRelease;
    private int rank;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public RankingSnapshot(User user, PeriodType periodType, String periodKey,
                           int totalPoints, int totalSessions, int totalHits,
                           int bestRelease, int avgRelease, int rank) {
        this.user = user;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.totalPoints = totalPoints;
        this.totalSessions = totalSessions;
        this.totalHits = totalHits;
        this.bestRelease = bestRelease;
        this.avgRelease = avgRelease;
        this.rank = rank;
    }
}
