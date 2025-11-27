package com.sosd.insightnews.ai;

import com.sosd.insightnews.InsightNewsApplication;
import com.sosd.insightnews.domain.AIAnalysisResult;
import com.sosd.insightnews.service.AIService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = {InsightNewsApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class AITest {

    @Autowired
    private AIService aiService;

    // 1. 测试生成标题
    @Test
    void testGenerateTitle() {
        String content = "自1月23日起，武汉市进入封锁状态，770名在武汉三所高中学习的新疆学生被拘留在学校，禁止与外界接触。";
        String title = aiService.generateTitle(content);
        System.out.println("====== 生成的标题 ======");
        System.out.println(title);
        System.out.println("======================");
    }

    // 2. 测试纯文本真伪检测
    @Test
    void testTextDetection() {
        // 一个典型的假新闻例子
        String fakeNews = "震惊！科学家发现喝白开水会致癌！最新研究表明，水中含有大量的二氢化氧，长期饮用会导致体内酸碱失衡。";
        
        AIAnalysisResult result = aiService.detectText(fakeNews);
        
        System.out.println("====== 文本检测结果 ======");
        System.out.println("可信度: " + result.getCredibility());
        System.out.println("总结: " + result.getSummary());
        System.out.println("证据链条数: " + (result.getTextEvidenceChain() != null ? result.getTextEvidenceChain().size() : 0));
        if (result.getTextEvidenceChain() != null) {
            result.getTextEvidenceChain().forEach(e -> {
                System.out.println("  - 片段: " + e.getQuote());
                System.out.println("  - 理由: " + e.getReason());
            });
        }
        System.out.println("========================");
    }

    // 3. 测试图片取证 (使用网络 URL)
    @Test
    void testImageDetection() {
        // 使用阿里云文档中的示例图片，或者你可以换成任意公网图片 URL
        String imageUrl = "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20241022/emyrja/dog_and_girl.jpeg";
        
        AIAnalysisResult result = aiService.detectImage(imageUrl);
        
        System.out.println("====== 图片分析结果 ======");
        System.out.println("描述: " + result.getSummary());
        if (result.getImageEvidenceChain() != null) {
            result.getImageEvidenceChain().forEach(e -> {
                System.out.println("  - 标签: " + e.getLabel());
                System.out.println("  - 坐标: " + e.getBbox()); // [ymin, xmin, ymax, xmax]
                System.out.println("  - 说明: " + e.getDescription());
            });
        }
        System.out.println("========================");
    }

    // 4. 测试图文多模态一致性
    @Test
    void testMultimodalDetection() {
        String imageUrl = "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20241022/emyrja/dog_and_girl.jpeg";
        // 故意写一个不一致的描述
        String content = "这是一张拍摄于火星的照片，照片中有一只猫在驾驶飞船。";
        
        AIAnalysisResult result = aiService.detectMultimodal(imageUrl, content);
        
        System.out.println("====== 图文一致性结果 ======");
        System.out.println("是否一致: " + result.getIsConsistent());
        System.out.println("一致性评分: " + result.getConsistencyScore());
        System.out.println("分析: " + result.getAnalysis());
        System.out.println("==========================");
    }
}