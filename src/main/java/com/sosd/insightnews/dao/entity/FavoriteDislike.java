package com.sosd.insightnews.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("FavoriteDislike")
public class FavoriteDislike {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("target_id")
    private String targetId;

    /**
     * 操作对象类型：news / topic
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 操作类型：like (点赞), dislike (点踩), favorite (收藏)
     */
    @TableField("operation_type")
    private String operationType;

    @TableField("operator_id")
    private String operatorId;

    @TableField("operation_time")
    private Date operationTime;
}