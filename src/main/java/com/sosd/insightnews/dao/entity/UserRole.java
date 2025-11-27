package com.sosd.insightnews.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户角色表
 * 显式指定 TableField 防止 Linux 环境下大小写映射丢失
 */
@TableName(value ="UserRole")
@Data
public class UserRole {
    @TableId(value = "Id", type = IdType.AUTO)
    private Integer id;

    @TableField("UserId")
    private String userid;

    @TableField("RoleId")
    private String roleid;
}