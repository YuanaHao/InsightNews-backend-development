package com.sosd.insightnews.email.builder;

import com.sosd.insightnews.email.builder.AbstractEmailBuilder;

public class CodeEmailBuilder extends AbstractEmailBuilder {

    private final String code;

    public CodeEmailBuilder(String code) {
        this.code = code;
    }

    @Override
    protected String subject() {
        return "【InsightNews】登录验证码";
    }

    @Override
    protected String emailContent() {
        // 简单的 HTML 模板
        return "<html><body>" +
                "<h3>您的验证码是：<span style='color:red;font-size:20px'>" + code + "</span></h3>" +
                "<p>验证码有效期为 3 分钟，请勿泄露给他人。</p>" +
                "</body></html>";
    }
}