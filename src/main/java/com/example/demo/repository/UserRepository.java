package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {


    // [新增] 各家 ID 的查詢方法
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByFacebookId(String facebookId);
    Optional<User> findByLineId(String lineId);

    
    // 給 Google 用 (防止重複註冊，透過 Email 關聯)
    Optional<User> findByEmail(String email);

    
}