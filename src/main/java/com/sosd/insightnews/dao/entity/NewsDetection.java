package com.sosd.insightnews.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 新闻检测表
 * @TableName NewsDetection
 */
@TableName(value ="NewsDetection")
@Data
public class NewsDetection {
    /**
     * 主键 (已修正为 BIGINT)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 网址
     */
    private String url;

    /**
     * 上传用户 (显式映射)
     */
    @TableField("user_id")
    private String userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 新闻来源
     */
    private String source;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 新闻类型 (显式映射)
     */
    @TableField("news_type")
    private String newsType;

    /**
     * 可信度
     */
    private Integer credibility;

    /**
     * 新闻发布时间 (关键修正：显式指定数据库列名，解决 null 问题)
     */
    @TableField("publish_date")
    private Date publishDate;

    /**
     * 创建时间 (关键修正)
     */
    @TableField("creation_time")
    private Date creationTime;

    /**
     * 返回文本 (关键修正)
     */
    @TableField("response_text")
    private String responseText;

    /**
     * 证据链文本集合 (关键修正：显式指定数据库列名)
     */
    @TableField("evidence_chain")
    private String evidenceChain;
}