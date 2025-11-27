package com.sosd.insightnews.controller;

import com.sosd.insightnews.domain.R;
import com.sosd.insightnews.exception.http.BadRequestException;
import com.sosd.insightnews.service.AliService;
import com.sosd.insightnews.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/common")
@Slf4j
public class CommonController {

    @Autowired
    private UserService userService;

    @Autowired
    private AliService aliService;

    // 修改为邮箱正则表达式
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";

    /**
     * 发送邮箱验证码
     * @param email 邮箱地址
     * @return 操作结果
     **/
    @RequestMapping(value = "/code", method = RequestMethod.POST)
    public R<Object> sendCode(String email) throws Exception {
        if (email == null || !email.matches(EMAIL_REGEX)) {
            throw new BadRequestException("邮箱格式错误");
        }
        userService.sendEmailCode(email);
        return R.ok("验证码已发送至邮箱", null);
    }

    /**
     * 文件上传
     * @param file 文件
     * @return 文件路径
     **/
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public R<String> upload(MultipartFile file) throws IOException, ExecutionException, InterruptedException {
        // 调用 AliService 进行文件上传
        CompletableFuture<String> url = aliService.uploadFile(file);
        return R.ok("文件上传成功", url.get());
    }
}