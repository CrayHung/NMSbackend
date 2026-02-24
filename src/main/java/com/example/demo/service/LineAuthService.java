package com.example.demo.service;

import com.example.demo.model.AuthProvider;
import com.example.demo.model.LoginLog;
import com.example.demo.model.LoginStatus;
import com.example.demo.model.User;
import com.example.demo.repository.LoginLogRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;

@Service
public class LineAuthService {

    @Value("${LINE_CHANNEL_ID}")
    private String channelId;

    @Value("${LINE_CHANNEL_SECRET}")
    private String channelSecret;

    @Value("${LINE_CALLBACK_URL}")
    private String callbackUrl;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LoginLogRepository loginLogRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public User lineLogin(String code, String ipAddress) {
        try {
            // 1. Get Token
            String tokenUrl = "https://api.line.me/oauth2/v2.1/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("grant_type", "authorization_code");
            map.add("code", code);
            map.add("redirect_uri", callbackUrl);
            map.add("client_id", channelId);
            map.add("client_secret", channelSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            String accessToken = objectMapper.readTree(response.getBody()).path("access_token").asText();

            // 2. Get Profile
            String profileUrl = "https://api.line.me/v2/profile";
            HttpHeaders profileHeaders = new HttpHeaders();
            profileHeaders.set("Authorization", "Bearer " + accessToken);
            HttpEntity<String> profileRequest = new HttpEntity<>(profileHeaders);
            ResponseEntity<String> profileResponse = restTemplate.exchange(profileUrl, HttpMethod.GET, profileRequest,
                    String.class);

            JsonNode profileRoot = objectMapper.readTree(profileResponse.getBody());
            String lineUserId = profileRoot.path("userId").asText();
            String displayName = profileRoot.path("displayName").asText();
            String pictureUrl = profileRoot.path("pictureUrl").asText();
            
      
            // 嘗試取得真實 email (需 LINE login 權限設定)，若無則生成虛擬 Email
            String email = profileRoot.path("email").asText(null);
            if (email == null || email.isEmpty()) {
                // 生成格式： {LineID}@line.user
                email = lineUserId + "@line.user";
            }
            // 為了 Lambda 表達式使用 (必須是 final)
            final String finalEmail = email;


            // 3. Save User
            User user = userRepository.findByLineId(lineUserId)
                    .map(existingUser -> {
                        existingUser.setName(displayName);
                        existingUser.setPictureUrl(pictureUrl);
                        existingUser.setLastLoginTime(LocalDateTime.now());
                        existingUser.setProvider(AuthProvider.LINE);

                        // [補充] 如果舊資料的 email 是空的，也幫他補上，不然登入後一樣會壞掉
                        if (existingUser.getEmail() == null || existingUser.getEmail().isEmpty()) {
                            existingUser.setEmail(finalEmail);
                        }

                        return existingUser;
                    })
                    .orElseGet(() -> {
                        // 使用 User(email, name, pictureUrl, provider) 建構子
                        User newUser = new User(finalEmail, displayName, pictureUrl, AuthProvider.LINE);
                        newUser.setLineId(lineUserId);
                        return newUser;
                    });

            User savedUser = userRepository.save(user);

            // 將這段註解調並改道controller裡面去驗證active/inactive, 不然註冊完設定好email會因為跑RuntimeException導致資料沒有成功被存入資料庫
            // // [檢查帳號狀態]
            // if (!savedUser.isActive()) {
            //     loginLogRepository.save(new LoginLog(savedUser.getId(), savedUser.getName(), ipAddress,
            //             LoginStatus.FAILURE, "Account Inactive"));
            //     // 這是一個 RuntimeException
            //     throw new RuntimeException("您的帳號尚未啟用或已被停用，請聯繫管理員");
            // }

            
            // 4. Log Success
            loginLogRepository.save(
                    new LoginLog(savedUser.getId(), savedUser.getName(), ipAddress, LoginStatus.SUCCESS, "LINE Login Success"));
            return savedUser;

        } catch (RuntimeException e) {
            // [修正] 優先捕捉 RuntimeException (包含我們自己拋出的 "尚未啟用")
            // 直接往上拋，讓 Controller 接收到正確的錯誤訊息
            throw e;
        } catch (Exception e) {
            // [修正] 捕捉其餘 Checked Exceptions (如 JsonProcessingException)
            // 將其包裝成 RuntimeException，避免編譯錯誤
            loginLogRepository.save(new LoginLog(null, "LINE_Unknown", ipAddress, LoginStatus.FAILURE, e.getMessage()));
            throw new RuntimeException("LINE Login Failed: " + e.getMessage(), e);
        }
    }
}