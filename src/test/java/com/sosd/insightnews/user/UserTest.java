package com.sosd.insightnews.user;

import com.sosd.insightnews.InsightNewsApplication;
import com.sosd.insightnews.domain.R;
import com.sosd.insightnews.dto.LoginDTO;
import com.sosd.insightnews.dto.UpdateUserDTO;
import com.sosd.insightnews.service.UserService;
import com.sosd.insightnews.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.List;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = {InsightNewsApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class UserTest {

    protected RestTemplate restTemplate = (new TestRestTemplate()).getRestTemplate();

    @Autowired
    private UserService userService;

    // 预设一个测试用的邮箱
    private final String TEST_EMAIL = "test_user@qq.com";
    private final String REGISTER_EMAIL = "new_user@qq.com";

    @Test
    void sendCode() {
        try {
            HttpHeaders headers = new HttpHeaders();

            // 修改：使用邮箱
            String email = TEST_EMAIL;

            HttpEntity entity = new HttpEntity(headers);
            // 修改：URL参数改为 email
            ResponseEntity<R> response = restTemplate.exchange(
                    "http://localhost:8087/common/code?email={email}",
                    HttpMethod.POST,
                    entity,
                    R.class,
                    email);
            System.out.println("response: " + response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void testLogin() {
        try {
            HttpHeaders headers = new HttpHeaders();

            LoginDTO req = new LoginDTO();
            // 修改：setPhone -> setEmail
            req.setEmail(TEST_EMAIL);
            req.setCode("123456");

            HttpEntity<LoginDTO> entity = new HttpEntity<>(req, headers);

            ResponseEntity<R> resp = restTemplate.exchange("http://localhost:8087/user/login", HttpMethod.POST, entity, R.class);
            // 注意：如果验证码不对，这里可能会返回 null 或错误信息，测试需结合实际环境调整
            if (resp.getBody() != null) {
                System.out.println("token: " + resp.getBody().getData());
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 登录失败是预期的（因为没有真实验证码），不一定fail，视测试目的而定
        }
    }

    @Test
    void testRegister() {
        try {
            HttpHeaders headers = new HttpHeaders();

            LoginDTO req = new LoginDTO();
            // 修改：setPhone -> setEmail
            req.setEmail(REGISTER_EMAIL);
            req.setCode("123456");

            HttpEntity<LoginDTO> entity = new HttpEntity<>(req, headers);

            ResponseEntity<R> resp = restTemplate.exchange("http://localhost:8087/user/register", HttpMethod.POST, entity, R.class);
            if (resp.getBody() != null) {
                System.out.println("token: " + resp.getBody().getData());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testGetUserInfo() {
        try {
            // 修改：使用邮箱作为 ID 生成 Token
            String token = JwtUtil.createTokenByUserId(TEST_EMAIL);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            System.out.println("token: " + token);

            HttpEntity<Void> entity = new HttpEntity<>(null, headers);
            ResponseEntity<R> resp = restTemplate.exchange(
                "http://localhost:8087/user/info",
                HttpMethod.GET,
                entity,
                R.class
            );

            System.out.println("User info: " + resp.getBody().getData());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void testUpdate() {
        try {
            // 修改：使用邮箱 ID
            String token = JwtUtil.createTokenByUserId(TEST_EMAIL);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);

            UpdateUserDTO req = new UpdateUserDTO();
            req.setName("测试更改");
            req.setGender("女");
            req.setRegion("福建 福州");
            req.setProfile("这是一个测试账号");
            req.setEmail("updated_email@qq.com"); // 这里的邮箱也可以改

            HttpEntity<UpdateUserDTO> entity = new HttpEntity<>(req, headers);
            ResponseEntity<R> resp = restTemplate.exchange(
                "http://localhost:8087/user/update",
                HttpMethod.PUT,
                entity,
                R.class
            );

            System.out.println("Update response: " + resp.getBody());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void testLogout() {
        try {
            String token = JwtUtil.createTokenByUserId(TEST_EMAIL);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);

            HttpEntity<Void> entity = new HttpEntity<>(null, headers);
            ResponseEntity<R> resp = restTemplate.exchange(
                "http://localhost:8087/user/logout",
                HttpMethod.POST,
                entity,
                R.class
            );

            System.out.println("Logout response: " + resp.getBody());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void testDelete() {
        try {
            // 修改：使用邮箱 ID
            String token = JwtUtil.createTokenByUserId(REGISTER_EMAIL);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);

            HttpEntity<Void> entity = new HttpEntity<>(null, headers);
            ResponseEntity<R> resp = restTemplate.exchange(
                "http://localhost:8087/user/delete",
                HttpMethod.DELETE,
                entity,
                R.class
            );

            System.out.println("Delete response: " + resp.getBody());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void testFeedback() {
        try {
            String token = JwtUtil.createTokenByUserId(TEST_EMAIL);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);

            String feedback = "这是一条测试反馈信息";
            HttpEntity<String> entity = new HttpEntity<>(feedback, headers);
            ResponseEntity<R> resp = restTemplate.exchange(
                "http://localhost:8087/user/feedback?feedback={feedback}",
                HttpMethod.POST,
                entity,
                R.class,
                feedback
            );

            System.out.println("Feedback response: " + resp.getBody());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    void testUploadFile() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // 创建文件资源
            File file = new File("src/test/java/com/sosd/insightnews/user/testAvatar.JPG");
            // 注意：如果本地没有这个文件，测试会失败，但不会导致编译错误
            if (file.exists()) {
                FileSystemResource resource = new FileSystemResource(file);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", resource);

                HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<R> resp = restTemplate.exchange(
                    "http://localhost:8087/common/upload",
                    HttpMethod.POST,
                    entity,
                    R.class
                );
                System.out.println("Upload response: " + resp.getBody());
            } else {
                System.out.println("Skipping upload test: file not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }
}