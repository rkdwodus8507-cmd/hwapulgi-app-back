package com.hwapulgi.api.promotion.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_point_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_point_grant_user_date",
                columnNames = {"user_id", "grant_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPointGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "grant_date", nullable = false)
    private LocalDate grantDate;

    @Column(nullable = false)
    private String promotionCode;

    @Column(nullable = false)
    private int amount;

    private String tossKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public DailyPointGrant(Long userId, LocalDate grantDate, String promotionCode, int amount) {
        this.userId = userId;
        this.grantDate = grantDate;
        this.promotionCode = promotionCode;
        this.amount = amount;
        this.status = GrantStatus.REQUESTED;
    }

    public void markCompleted(String tossKey) {
        this.tossKey = tossKey;
        this.status = GrantStatus.COMPLETED;
    }
}
