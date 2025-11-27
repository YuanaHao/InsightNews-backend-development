package com.sosd.insightnews.service;

import com.sosd.insightnews.InsightNewsApplication;
import com.sosd.insightnews.context.UserContext;
import com.sosd.insightnews.domain.UserDo;
import com.sosd.insightnews.dto.NewsDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest(classes = InsightNewsApplication.class)
@Transactional
public class NewsDetectionServiceTest {

    @Autowired
    private NewsDetectionService newsDetectionService;

    // 模拟一个用户ID
    private final String TEST_USER_ID = "test_user_001";

    @BeforeEach
    public void setup() {
        // 模拟用户登录上下文
        UserDo mockUser = new UserDo();
        mockUser.setId(TEST_USER_ID);
        mockUser.setName("测试用户");
        UserContext.setCurrentUser(mockUser);
    }

    @AfterEach
    public void tearDown() {
        // 清理上下文，防止影响其他测试
        UserContext.clear();
    }

    @Test
    public void testFullFlowTextDetection() {
        System.out.println("====== 开始测试：文本新闻检测链路 ======");
        String content = "经查证，关于'喝凉水长胖'的说法是缺乏科学依据的谣言。水的卡路里为零，不会导致脂肪堆积。";

        // 1. 执行上传和检测
        NewsDTO result = newsDetectionService.uploadTextNews(content, TEST_USER_ID);

        // 2. 验证结果不为空
        Assertions.assertNotNull(result, "返回结果不应为空");
        Assertions.assertNotNull(result.getId(), "存入数据库后应当生成ID");
        
        // 3. 验证 AI 分析字段
        System.out.println("生成的标题: " + result.getTitle());
        System.out.println("可信度: " + result.getCredibility());
        
        // 4. 验证历史记录缓存 (Redis/DB)
        List<NewsDTO> history = newsDetectionService.getHistoryDetections(TEST_USER_ID);
        Assertions.assertTrue(history.stream().anyMatch(h -> h.getId().equals(result.getId())), "历史记录中应包含刚检测的新闻");
        
        System.out.println("====== 文本检测链路测试通过 ======");
    }

    @Test
    public void testFullFlowImageDetection() {
        System.out.println("====== 开始测试：图片新闻检测链路 ======");
        // 使用公网可访问的测试图片
        String imgUrl = "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20241022/emyrja/dog_and_girl.jpeg";

        // 1. 执行图片检测
        NewsDTO result = newsDetectionService.uploadFileNews(imgUrl, TEST_USER_ID);

        // 2. 验证
        Assertions.assertNotNull(result);
        
        System.out.println("图片分析摘要: " + result.getTitle());
        
        System.out.println("====== 图片检测链路测试通过 ======");
    }
}