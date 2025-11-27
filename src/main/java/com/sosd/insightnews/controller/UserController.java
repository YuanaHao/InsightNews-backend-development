package com.sosd.insightnews.controller;

import com.sosd.insightnews.context.UserContext;
import com.sosd.insightnews.converter.UserConverter;
import com.sosd.insightnews.domain.R;
import com.sosd.insightnews.domain.UserDo;
import com.sosd.insightnews.dto.LoginDTO;
import com.sosd.insightnews.dto.TopicDTO;
import com.sosd.insightnews.dto.UpdateUserDTO;
import com.sosd.insightnews.dto.UserDTO;
import com.sosd.insightnews.email.EmailService;
import com.sosd.insightnews.email.builder.FeedbackEmailBuilder;
import com.sosd.insightnews.exception.http.BadRequestException;
import com.sosd.insightnews.service.ScienceTopicService;
import com.sosd.insightnews.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;


@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ScienceTopicService scienceTopicService;

    // 严格的邮箱校验正则（用于登录注册，不允许 "default"）
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
        
    // 宽松的邮箱校验正则（用于用户信息修改，允许 "default"）
    private static final String EMAIL_REGEX_UPDATE = "^(default|[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+)$";

    /**
         * 用户登录
         * @param loginDTO 登录信息
         * @return 登录成功后的token
         **/
    @PostMapping("/login")
    public R<String> login(@RequestBody LoginDTO loginDTO) {
        // 修改点：获取邮箱并校验
        String email = loginDTO.getEmail();
        if (email == null || !email.matches(EMAIL_REGEX)) {
            throw new BadRequestException("邮箱格式错误");
        }
        String token = userService.login(loginDTO);
        return R.ok("用户登录成功", token);
    }

    /**
         * 用户注册
         * @param loginDTO 注册信息
         * @return 注册成功后的token
         **/
    @PostMapping("/register")
    public R<String> register(@RequestBody LoginDTO loginDTO) throws BadRequestException{
        // 修改点：获取邮箱并校验
        String email = loginDTO.getEmail();
        if (email == null || !email.matches(EMAIL_REGEX)) {
            throw new BadRequestException("邮箱格式错误");
        }
        String token = userService.register(loginDTO);
        return R.ok("用户注册成功", token);
    }

    /**
     * 获取用户信息
     * @return 用户信息
     **/
    @GetMapping("/info")
    public R<UserDTO> getUserInfo() throws IOException {
        UserDo userDo = UserContext.getCurrentUser();
        UserDTO result = UserConverter.do2dto(userDo);
        return R.ok("获取用户信息成功",result);
    }

    /**
     * 修改用户信息
     * @param updateUserDTO 修改信息
     * @return 修改成功后的用户信息
     **/
    @PutMapping("/update")
    @Transactional
    public R<String> update(@RequestBody UpdateUserDTO updateUserDTO){
        UserDo userDo = UserContext.getCurrentUser();
        if(!updateUserDTO.getEmail().equals("default")){
            if(!updateUserDTO.getEmail().matches(EMAIL_REGEX_UPDATE)){
                throw new BadRequestException("邮箱格式错误");
            }
        }
        UserDo o = UserConverter.dto2do(updateUserDTO);
        o.setId(userDo.getId());
        userService.updateInfo(o);
        log.info("Users modify personal information：{}",updateUserDTO);
        return R.ok("修改用户信息成功",null);
    }

    /**
     * 用户退出登录
     * @return 退出成功信息
     **/
    @PostMapping("/logout")
    public R<Object> logout(){
        userService.logout();
        return R.ok("用户退出登录",null);
    }

    /**
     * 用户注销
     * @return 注销成功信息
     **/
    @DeleteMapping("/delete")
    @Transactional
    public R<Object> delete(){
        UserDo userDo = UserContext.getCurrentUser();
        userService.delete(userDo.getId());
        return R.ok("用户注销成功",null);
    }

    /**
         * 发送反馈邮件给开发者
         * @param feedback 反馈内容
         * @return 发送成功信息
         **/
    @PostMapping("/feedback")
    public R<Object> feedback(@RequestParam String feedback) {
        // 1. 获取当前登录用户信息
        UserDo userDo = UserContext.getCurrentUser();
        
        // 2. 简单校验（虽然主要目的是发给开发者，但通常要求用户绑定邮箱方便后续联系）
        if (userDo.getEmail() == null || "default".equals(userDo.getEmail())) {
            throw new BadRequestException("请先绑定邮箱方便我们需要时联系您");
        }

        try {
            // 3. 拼接完整的邮件内容（包含用户身份信息）
            // 使用 HTML 格式换行 <br>，因为 EmailService 解析的是 HTML
            String finalContent = String.format(
                "<h3>【InsightNews 用户反馈】</h3>" +
                "<p><strong>用户ID:</strong> %s</p>" +
                "<p><strong>用户昵称:</strong> %s</p>" +
                "<p><strong>用户邮箱:</strong> %s</p>" +
                "<hr/>" +
                "<h4>反馈内容：</h4>" +
                "<p>%s</p>",
                userDo.getId(),
                userDo.getName(),
                userDo.getEmail(),
                feedback
            );

            // 4. 构建邮件
            FeedbackEmailBuilder builder = new FeedbackEmailBuilder(finalContent);

            // 5. 【关键修改】发送给开发者（请在这里填入你接收反馈的邮箱，例如你的 QQ 邮箱）
            String developerEmail = "yuanahao574@gmail.com"; 
            
            emailService.send(builder.build(developerEmail));

        } catch (Exception e) {
            e.printStackTrace();
            throw new BadRequestException("反馈发送失败，请稍后重试");
        }
        
        return R.ok("反馈已提交，开发者会尽快查看", null);
    }

    /**
     * 获取用户收藏的话题列表
     * @return 话题列表
     */
    @GetMapping("/favorite/topics")
    public R<List<TopicDTO>> getFavoriteTopics() {
        String userId = UserContext.getCurrentUser().getId();
        log.info("获取用户收藏的话题列表, userId:{}", userId);
        List<TopicDTO> topics = scienceTopicService.getFavoriteTopics(userId);
        return R.ok("成功获取用户收藏的话题列表", topics);
    }

    // 获取新闻收藏 路径参数是userId 返回的是一个List<NewsDTO>

}
