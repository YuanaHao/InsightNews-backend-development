package com.sosd.insightnews.domain;

import lombok.Data;
import java.util.List;

/**
 * AI 分析结果封装类
 */
@Data
public class AIAnalysisResult {
    // 通用字段
    private Integer credibility;      // 可信度 0-100
    private String summary;           // 总结/描述
    private String analysis;          // 详细分析文本

    // 文本模式字段
    private List<TextEvidence> textEvidenceChain;

    // 图片/多模态模式字段
    private List<ImageEvidence> imageEvidenceChain;
    
    // 多模态一致性字段
    private Boolean isConsistent;     // 图文是否一致
    private Integer consistencyScore; // 一致性评分

    @Data
    public static class TextEvidence {
        private String quote;   // 原文片段
        private String reason;  // 佐证理由
        private Float score;    // 片段可信度
    }

    @Data
    public static class ImageEvidence {
        private String label;        // 标记名称
        private String description;  // 详细说明
        // 坐标格式 [x1, y1, x2, y2] (归一化 0-1000)
        private List<Integer> bbox;  
    }
}