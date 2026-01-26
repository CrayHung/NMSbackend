// src/main/java/com/example/demo/model/LoginLog.java
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_logs")
@Data
@NoArgsConstructor
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 修改：明確指定欄位名稱為 user_id，避免 hibernate 自動命名不一致
    @Column(name = "user_id")
    private Long userId;

    // [新增] 關聯 User 物件
    // 這讓前端可以拿到 nested JSON: { "user": { "name": "...", "pictureUrl": "..." } }
    // insertable/updatable = false 表示寫入時依賴上面的 userId 欄位，唯讀取出用
    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    private String username;      // 登入當下的名稱快照 (若 User 被刪除，這裡還留有紀錄)
    
    private String ipAddress;
    
    @Enumerated(EnumType.STRING)
    private LoginStatus status;

    private String message;
    
    private LocalDateTime loginTime;

    // 建構子 (保持不變，Service 不需要改)
    public LoginLog(Long userId, String username, String ipAddress, LoginStatus status, String message) {
        this.userId = userId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.status = status;
        this.message = message;
        this.loginTime = LocalDateTime.now();
    }
}