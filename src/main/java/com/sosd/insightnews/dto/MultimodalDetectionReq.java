package com.sosd.insightnews.dto;

import lombok.Data;

@Data
public class MultimodalDetectionReq {
    /**
     * 图片链接
     */
    private String imageUrl;

    /**
     * 配套文本
     */
    private String content;
}