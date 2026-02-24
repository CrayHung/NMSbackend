package com.example.demo.repository;

import com.example.demo.model.ChirpStackApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChirpStackAppRepository extends JpaRepository<ChirpStackApp, String> {
    // Spring Data JPA 會自動實作 CRUD，不需要寫任何程式碼
}