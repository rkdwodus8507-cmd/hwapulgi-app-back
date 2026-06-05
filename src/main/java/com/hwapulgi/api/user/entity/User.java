package com.hwapulgi.api.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String nickname;

    @Column(unique = true, nullable = true)
    private String deviceId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static User guest(String deviceId, String nickname) {
        User u = new User();
        u.deviceId = deviceId;
        u.nickname = nickname;
        return u;
    }

    public static User tossUser(String externalId, String nickname) {
        User u = new User();
        u.externalId = externalId;
        u.nickname = nickname;
        return u;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
