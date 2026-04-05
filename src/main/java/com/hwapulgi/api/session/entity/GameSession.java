package com.hwapulgi.api.session.entity;

import com.hwapulgi.api.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String target;

    private String customTarget;

    @Column(nullable = false)
    private String targetNickname;

    @Column(nullable = false)
    private int angerBefore;

    @Column(nullable = false)
    private int angerAfter;

    @Column(nullable = false)
    private int hits;

    @Column(nullable = false)
    private int skillShots;

    @Column(nullable = false)
    private int releasedPercent;

    @Column(nullable = false)
    private int points;

    private String memo;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public GameSession(User user, String target, String customTarget, String targetNickname,
                       int angerBefore, int angerAfter, int hits, int skillShots,
                       int releasedPercent, int points, String memo) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.target = target;
        this.customTarget = customTarget;
        this.targetNickname = targetNickname;
        this.angerBefore = angerBefore;
        this.angerAfter = angerAfter;
        this.hits = hits;
        this.skillShots = skillShots;
        this.releasedPercent = releasedPercent;
        this.points = points;
        this.memo = memo;
    }

    public void updateAngerAfter(int angerAfter) {
        this.angerAfter = angerAfter;
        if (this.angerBefore > 0) {
            this.releasedPercent = (int) (((double)(this.angerBefore - angerAfter) / this.angerBefore) * 100);
        }
    }
}
