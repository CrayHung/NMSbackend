package com.example.demo.repository;

import com.example.demo.model.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    // 查詢特定使用者的紀錄
    List<LoginLog> findByUserIdOrderByLoginTimeDesc(Long userId);
    
    // [新增] 根據 userId 刪除所有紀錄 (刪除使用者前必須執行)
    void deleteByUserId(Long userId);
}