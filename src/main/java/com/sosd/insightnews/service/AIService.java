package com.sosd.insightnews.service;

import com.sosd.insightnews.domain.AIAnalysisResult;

public interface AIService {

    /**
     * 简单的标题生成 (保留旧接口)
     */
    String generateTitle(String content);

    /**
     * 纯文本新闻检测
     */
    AIAnalysisResult detectText(String content);

    /**
     * 纯图片新闻检测 (支持 HTTP URL 或 MultipartFile)
     */
    AIAnalysisResult detectImage(Object imageSource);

    /**
     * 图文多模态检测
     * @param imageSource 图片 (URL 或 MultipartFile)
     * @param content 文本内容
     */
    AIAnalysisResult detectMultimodal(Object imageSource, String content);
}