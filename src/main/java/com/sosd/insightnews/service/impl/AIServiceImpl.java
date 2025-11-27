package com.sosd.insightnews.service.impl;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosd.insightnews.constant.AIPrompts;
import com.sosd.insightnews.domain.AIAnalysisResult;
import com.sosd.insightnews.service.AIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@Slf4j
public class AIServiceImpl implements AIService {

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    private static final String MODEL_NAME = "qwen3-vl-plus";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 生成标题（实现逻辑：调用大模型进行总结）
     */
    @Override
    public String generateTitle(String content) {
        log.info("开始生成新闻标题，内容长度: {}", content.length());
        // 直接调用底层方法，获取纯文本响应
        return fetchRawResponse(AIPrompts.TITLE_GENERATION_SYSTEM, null, content);
    }

    @Override
    public AIAnalysisResult detectText(String content) {
        log.info("开始纯文本检测...");
        String jsonRaw = fetchRawResponse(AIPrompts.TEXT_DETECTION_SYSTEM, null, content);
        return parseAIResponse(jsonRaw);
    }

    @Override
    public AIAnalysisResult detectImage(Object imageSource) {
        log.info("开始图片分析...");
        String jsonRaw = fetchRawResponse(AIPrompts.IMAGE_DETECTION_SYSTEM, imageSource, "请分析图片真实性并标出证据。");
        return parseAIResponse(jsonRaw);
    }

    @Override
    public AIAnalysisResult detectMultimodal(Object imageSource, String content) {
        log.info("开始图文多模态检测...");
        String jsonRaw = fetchRawResponse(AIPrompts.MULTIMODAL_DETECTION_SYSTEM, imageSource, content);
        return parseAIResponse(jsonRaw);
    }

    /**
     * 核心私有方法：调用 DashScope API 获取原始文本响应
     * @param systemPrompt 系统提示词
     * @param imageInput 图片输入 (String URL 或 MultipartFile)
     * @param userText 用户文本
     * @return 模型返回的原始字符串
     */
    private String fetchRawResponse(String systemPrompt, Object imageInput, String userText) {
        try {
            MultiModalConversation conv = new MultiModalConversation();
            List<Map<String, Object>> contentList = new ArrayList<>();

            // 1. 处理图片输入
            if (imageInput != null) {
                Map<String, Object> imageMap = new HashMap<>();
                if (imageInput instanceof String) {
                    imageMap.put("image", (String) imageInput);
                } else if (imageInput instanceof MultipartFile) {
                    MultipartFile file = (MultipartFile) imageInput;
                    String base64Content = Base64.getEncoder().encodeToString(file.getBytes());
                    String mimeType = file.getContentType() != null ? file.getContentType() : "image/png";
                    String dataUrl = "data:" + mimeType + ";base64," + base64Content;
                    imageMap.put("image", dataUrl);
                }
                contentList.add(imageMap);
            }

            // 2. 处理用户文本
            if (userText != null && !userText.isEmpty()) {
                contentList.add(Collections.singletonMap("text", userText));
            }

            // 3. 构建消息
            MultiModalMessage systemMessage = MultiModalMessage.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(List.of(Collections.singletonMap("text", systemPrompt)))
                    .build();

            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(contentList)
                    .build();

            // 4. 构建参数
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model(MODEL_NAME)
                    .messages(Arrays.asList(systemMessage, userMessage))
                    .topP(0.8)
                    .build();

            // 5. 发起调用
            MultiModalConversationResult result = conv.call(param);

            // 6. 返回内容
            return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();

        } catch (Exception e) {
            log.error("AI 服务调用异常", e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage());
        }
    }

    /**
     * 辅助方法：将 JSON 字符串解析为对象
     */
    private AIAnalysisResult parseAIResponse(String rawText) {
        try {
            String jsonStr = rawText.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            return objectMapper.readValue(jsonStr, AIAnalysisResult.class);
        } catch (Exception e) {
            log.error("JSON解析失败, 原始内容: {}", rawText, e);
            AIAnalysisResult fallback = new AIAnalysisResult();
            fallback.setAnalysis(rawText);
            fallback.setSummary("AI 响应格式解析失败，但已获取分析文本。");
            // 设置默认低可信度以引起注意
            fallback.setCredibility(50); 
            return fallback;
        }
    }
}