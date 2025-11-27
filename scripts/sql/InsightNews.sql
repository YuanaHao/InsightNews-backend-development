# ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS InsightNews DEFAULT CHARACTER SET = utf8mb4;

Use InsightNews;

# 用户表
DROP TABLE IF EXISTS `User`;

CREATE TABLE `User`
(
    # 关键信息
    `id`          varchar(32)  NOT NULL COMMENT '主键',
    `phone`       varchar(64)  NOT NULL COMMENT '手机号码',
    # 展示信息
    `name`        varchar(64)  NOT NULL DEFAULT 'default' COMMENT '昵称',
    `avatar`      varchar(255) NOT NULL DEFAULT 'default' COMMENT '头像',
    `gender`      varchar(16)  NOT NULL DEFAULT 'SECRET'  COMMENT '性别',
    `region`      varchar(64)  NOT NULL DEFAULT 'default' COMMENT '地区',
    `profile`     varchar(64)  NOT NULL DEFAULT 'default' COMMENT '简介',
    `email`       varchar(64)  NOT NULL DEFAULT 'default' COMMENT 'qq邮箱',
    # 微信公众号信息
    `open_id`     varchar(64)  NOT NULL DEFAULT 'default' COMMENT '微信OpenId',
    
    # 修正：补充缺失字段，并统一使用下划线命名
    `create_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `is_deleted`  tinyint      DEFAULT '0' COMMENT '逻辑删除',
    
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';


# 角色表 RBAC
DROP TABLE IF EXISTS `Role`;

CREATE TABLE `Role`
(
    `Id`          int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `RoleId`      varchar(255)     NOT NULL COMMENT '角色名',
    `Description` varchar(256)     NOT NULL DEFAULT 'default' COMMENT '备注',
    PRIMARY KEY (`Id`),
    UNIQUE KEY `UK_RoleId` (`RoleId`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';


# 用户角色映射表 RBAC
DROP TABLE IF EXISTS `UserRole`;

CREATE TABLE `UserRole`
(
    `Id`     int(10)     unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `UserId` varchar(32)      NOT NULL COMMENT '用户Id',
    `RoleId` varchar(255)     NOT NULL COMMENT '角色名',
    PRIMARY KEY (`Id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色表';


# 权限表 RBAC
DROP TABLE IF EXISTS `Permission`;

CREATE TABLE `Permission`
(
    `Id`        int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `Operation` varchar(128)     NOT NULL COMMENT '权限操作',
    `Target`    varchar(128)     NOT NULL COMMENT '权限对象',
    PRIMARY KEY (`Id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权限表';


# 角色权限映射表 RBAC
# 修正：表名拼写错误 RolePermisson -> RolePermission
DROP TABLE IF EXISTS `RolePermission`;

CREATE TABLE `RolePermission`
(
    `Id`          int(10)     unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
    `RoleId`      varchar(255)         NOT NULL COMMENT '角色名',
    `PermissionId` int(10)     unsigned NOT NULL COMMENT '权限id',
    PRIMARY KEY (`Id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4 COMMENT='角色权限表';

DROP TABLE IF EXISTS `FavoriteDislike`;

# 收藏点踩表
CREATE TABLE `FavoriteDislike`
(
    # 关键信息
    # 【重点修改】在这里加上了 AUTO_INCREMENT
    `id`              int(10) unsigned   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `target_id`       varchar(32)    NOT NULL COMMENT '操作对象ID',
    `target_type`     varchar(32)    NOT NULL COMMENT '操作对象类型（话题/新闻）',
    `operation_type`  varchar(32)    NOT NULL COMMENT '操作类型(点踩收藏关注)',
    `operator_id`     varchar(32)    NOT NULL COMMENT '操作者ID',
    `operation_time`  timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '操作时间',

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='收藏点踩表';

DROP TABLE IF EXISTS `Comments`;

# 评论留言表
CREATE TABLE `Comments`
(
    # 关键信息
    `id`              int(10) unsigned         NOT NULL COMMENT '主键',
    `user_id`         varchar(32)              NOT NULL COMMENT '用户ID',
    `comment`         varchar(255)             NOT NULL COMMENT '留言内容',
    `topic_id`        int(10)                  NOT NULL COMMENT '话题ID',
    `root_comment_id` int(10)                  DEFAULT NULL COMMENT '顶级评论ID',
    `status`          tinyint                  NOT NULL COMMENT '业务状态：1 评论 2 回复',
    `created_at`      timestamp                NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `is_deleted`      tinyint                  DEFAULT '0' COMMENT '是否删除(0未删除;1已删除)',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='话题评论表';

DROP TABLE IF EXISTS `ScienceTopic`;

# 科普话题表
CREATE TABLE `ScienceTopic`
(
    # 关键信息
    `id`              int(10) unsigned    NOT NULL COMMENT '主键',
    `title`           varchar(255)   NOT NULL COMMENT '标题',
    `description`     varchar(255)   NOT NULL COMMENT '总结',
    `content`         text           NOT NULL COMMENT '内容',
    `category`        varchar(64)    NOT NULL COMMENT '分类',
    `publish_time`    timestamp      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `topic_cover`     varchar(255)   DEFAULT NULL COMMENT '话题封面',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='科普话题表';

DROP TABLE IF EXISTS `NewsDetection`;

# 新闻检测表
CREATE TABLE `NewsDetection`
(
    # 关键信息：确认为 bigint
    `id`              bigint unsigned   NOT NULL COMMENT '主键',
    `url`             varchar(255)                COMMENT '网址',
    `user_id`         varchar(64)   NOT NULL COMMENT '上传用户',
    `title`           varchar(255)  NOT NULL COMMENT '标题',
    `source`          varchar(64)   NOT NULL COMMENT '新闻来源',
    `content`         text          DEFAULT NULL COMMENT '文本内容',
    `news_type`       varchar(32)   NOT NULL COMMENT '新闻类型',
    `credibility`     float         NOT NULL COMMENT '可信度',
    `publish_date`    timestamp     NOT NULL COMMENT '新闻发布时间',
    `creation_time`   timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `response_text`   text          DEFAULT NULL COMMENT '返回文本',
    `evidence_chain`  text          DEFAULT NULL COMMENT '证据链文本集合',

    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='新闻检测表';

# ------------------------------------------------------------
# 初始化基础数据 (确保系统启动即可用)
# ------------------------------------------------------------

-- 1. 初始化权限
INSERT IGNORE INTO Permission (Operation, Target) VALUES ('ModifyUser', 'Self');
INSERT IGNORE INTO Permission (Operation, Target) VALUES ('ViewNews', 'All');

-- 2. 初始化角色
INSERT IGNORE INTO Role (RoleId, Description) VALUES ('User', '普通用户');
INSERT IGNORE INTO Role (RoleId, Description) VALUES ('Admin', '管理员');

-- 3. 给 User 角色分配权限 (假设 Permission Id 为 1 和 2)
INSERT IGNORE INTO RolePermission (RoleId, PermissionId) VALUES ('User', 1);
INSERT IGNORE INTO RolePermission (RoleId, PermissionId) VALUES ('User', 2);