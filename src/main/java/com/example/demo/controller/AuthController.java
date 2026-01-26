// 接收前端傳來的 Code
//在所有登入方法 (Local, Google, Line, FB) 成功後，都產生 Token
package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.model.User;
import com.example.demo.repository.LoginLogRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtils;
import com.example.demo.service.GoogleAuthService;
import com.example.demo.service.LineAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.example.demo.service.FacebookAuthService;
import com.example.demo.service.LocalAuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
// @CrossOrigin(origins = "http://localhost:5173")   //統一由security管理
public class AuthController {

    @Autowired
    private LineAuthService lineAuthService;

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Autowired
    private FacebookAuthService facebookAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LocalAuthService localAuthService;


    // [新增] 開放給外部使用的註冊 API
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        try {
            String name = payload.get("name");
            String email = payload.get("email");
            String password = payload.get("password");
            
            // 呼叫 Service 進行註冊 (Service 內已經會加密密碼了)
            User user = localAuthService.register(name, email, password, "USER");
            
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Registration failed: " + e.getMessage());
        }
    }

    
    // 1. 本地登入
    @PostMapping("/login")
    public ResponseEntity<?> localLogin(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        try {
            // 先執行原本的登入驗證
            User user = localAuthService.login(payload.get("email"), payload.get("password"), getIpAddress(request));

            // 驗證成功 -> 產生 JWT
            String token = jwtUtils.generateToken(user.getEmail());

            // 回傳 Token + User
            return ResponseEntity.ok(new AuthResponse(token, user));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    // Line Login
    @PostMapping("/line")
    public ResponseEntity<?> lineLogin(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String code = payload.get("code");

        // 取得 IP 
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        if (code == null) {
            return ResponseEntity.badRequest().body("Code is required");
        }

        try {
            // 傳入 IP
            User user = lineAuthService.lineLogin(code, ipAddress);
            // 產生 JWT
            String token = jwtUtils.generateToken(user.getEmail());
            return ResponseEntity.ok(new AuthResponse(token, user));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Login failed: " + e.getMessage());
        }
    }

    // Google Login
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String code = payload.get("code");
        String redirectUri = payload.get("redirectUri"); // Google 驗證需要 redirectUri
        String ipAddress = getIpAddress(request);

        if (code == null)
            return ResponseEntity.badRequest().body("Code is required");
        if (redirectUri == null)
            return ResponseEntity.badRequest().body("Redirect URI is required");

        try {
            User user = googleAuthService.googleLogin(code, redirectUri, ipAddress);

            // 產生 JWT
            String token = jwtUtils.generateToken(user.getEmail());

            return ResponseEntity.ok(new AuthResponse(token, user));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Google Login failed: " + e.getMessage());
        }
    }

    // [新增] Facebook Login
    @PostMapping("/facebook")
    public ResponseEntity<?> facebookLogin(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String code = payload.get("code");
        // 前端傳來的 redirectUri，必須跟前端發起登入時設定的完全一樣
        String redirectUri = payload.get("redirectUri");

        String ipAddress = getIpAddress(request);

        if (code == null)
            return ResponseEntity.badRequest().body("Code is required");
        // Facebook 對 redirectUri 檢查極嚴格，建議從前端傳過來以確保一致
        if (redirectUri == null)
            return ResponseEntity.badRequest().body("Redirect URI is required");

        try {
            User user = facebookAuthService.facebookLogin(code, redirectUri, ipAddress);
            // 產生 JWT
            String token = jwtUtils.generateToken(user.getEmail());
            return ResponseEntity.ok(new AuthResponse(token, user));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Facebook Login failed: " + e.getMessage());
        }
    }


    // 取得所有登入紀錄
    @GetMapping("/history")
    public ResponseEntity<?> getAllLoginLogs() {
        // 依照時間倒序排列
        return ResponseEntity.ok(loginLogRepository.findAll(Sort.by(Sort.Direction.DESC, "loginTime")));
    }

    @GetMapping("/logs/{userId}")
    public ResponseEntity<?> getLoginLogs(@PathVariable Long userId) {
        return ResponseEntity.ok(loginLogRepository.findByUserIdOrderByLoginTimeDesc(userId));
    }

    // 輔助方法：取得 IP
    private String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }
}