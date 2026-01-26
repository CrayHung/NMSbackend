// User.java (修改)
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // [修改] 不再使用單一的 providerId，改為各家獨立欄位
    // 這樣 Google 和 FB 的 ID 可以共存
    @Column(unique = true)
    private String googleId;

    @Column(unique = true)
    private String lineId;

    @Column(unique = true)
    private String facebookId;

    // [保留] 用來記錄「最近一次」是用什麼方式登入的 (給前端顯示用)
    @Enumerated(EnumType.STRING)
    @JsonProperty("authProvider")
    private AuthProvider provider; // Last Login Provider

    private String name;

    @Column(unique = true)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String pictureUrl;

    private String role;

    // [新增] 密碼欄位 (只有 provider=LOCAL 時會有值，其他為 NULL)
    // 注意：不要回傳給前端 (加上 @JsonIgnore 避免資安外洩)
    @JsonIgnore
    private String password;

    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;

    // [新增] 帳號狀態與鎖定機制欄位
    @Column(nullable = false)
    private boolean active = true; // 預設為 true (給手動註冊用)，OAuth 需覆寫

    private int loginAttempts = 0; // 登入失敗次數

    private LocalDateTime lockTime; // 被鎖定的時間點

    // [修改] 建構子：建立新使用者時使用
    public User(String email, String name, String pictureUrl, AuthProvider provider) {
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.provider = provider;
        this.role = "USER";
        this.createTime = LocalDateTime.now();
        this.lastLoginTime = LocalDateTime.now();
        this.active = false; // OAuth 預設停用
        this.loginAttempts = 0;
    }
}