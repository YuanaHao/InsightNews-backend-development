package com.sosd.insightnews.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName(value ="RolePermission")
@Data
public class RolePermission {
    @TableId(value = "Id", type = IdType.AUTO)
    private Integer id;

    @TableField("RoleId")
    private String roleid;

    @TableField("PermissionId")
    private Integer permissionid;
}