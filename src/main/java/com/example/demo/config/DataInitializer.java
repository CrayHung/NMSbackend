//檢查「是否存在 Admin 帳號」，如果不存在，就自動建立一個

package com.example.demo.config;

import com.example.demo.model.AuthProvider;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 設定預設管理員的 帳號+Email  (admin/admin) 
        String adminEmail = "admin";

        // 檢查是否已經存在
        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            System.out.println("偵測到無管理員帳號，正在建立預設 Admin...");

            User admin = new User();
            admin.setName("System Admin");
            admin.setEmail(adminEmail);
            
            // 密碼經過加密
            admin.setPassword(passwordEncoder.encode("admin")); 
            
            admin.setRole("ADMIN");
            admin.setProvider(AuthProvider.LOCAL);
            // admin.setProviderId("local_" + adminEmail);
            admin.setCreateTime(LocalDateTime.now());
            
            // 若有頭像欄位可設為空或預設圖
            admin.setPictureUrl(null); 

            userRepository.save(admin);
            
            System.out.println("預設管理員建立成功！");
            System.out.println("帳號: " + adminEmail);
            System.out.println("密碼: admin");
        } else {
            System.out.println("管理員帳號admin/admin已存在，跳過建立步驟。");
        }
    }
}