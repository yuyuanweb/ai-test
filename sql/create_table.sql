# 数据库初始化
# @author <a href="https://codefather.cn">编程导航学习圈</a>

-- 创建库
create database if not exists ai_eval;

-- 切换库
use ai_eval;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;


-- 初始化数据
-- 密码是 12345678（MD5 加密 + 盐值 yupi）
INSERT INTO user (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole) VALUES
(1, 'admin', '10670d38ec32fa8102be6a37f8cb52bf', '管理员', 'https://www.codefather.cn/logo.png', '系统管理员', 'admin'),
(2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户', 'https://www.codefather.cn/logo.png', '我是一个普通用户', 'user'),
(3, 'test', '10670d38ec32fa8102be6a37f8cb52bf', '测试账号', 'https://www.codefather.cn/logo.png', '这是一个测试账号', 'user');

-- 对话记录表 (MVP核心表)
create table if not exists conversation
(
    id                  varchar(36) primary key comment '对话唯一标识',
    userId              bigint                             not null comment '用户ID',
    title               varchar(200)                       null comment '对话标题',
    conversationType    varchar(20)                        not null comment '对话类型: side_by_side/prompt_lab',
    models              json                               not null comment '参与的模型列表',
    codePreviewEnabled  tinyint  default 0                 not null comment '是否启用代码预览（1-启用 0-不启用）',
    totalTokens         int      default 0                 null comment '总Token消耗',
    totalCost           decimal(10, 4) default 0           null comment '总成本(USD)',
    createTime          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime          datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete            tinyint  default 0                 not null comment '逻辑删除',
    index idx_user_created (userId, createTime desc, isDelete),
    index idx_type (conversationType, isDelete),
    index idx_code_preview (codePreviewEnabled, isDelete)
) comment '对话记录表' collate = utf8mb4_unicode_ci;

-- 对话消息表 (MVP核心表)
create table if not exists conversation_message
(
    id              varchar(36) primary key comment '消息唯一标识',
    conversationId  varchar(36)                        not null comment '对话ID',
    userId          bigint                             not null comment '用户ID',
    messageIndex    int                                not null comment '消息序号(从0开始)',
    role            varchar(20)                        not null comment '角色: user/assistant',
    modelName       varchar(100)                       null comment '模型名称(assistant消息)',
    variantIndex    int                                null comment '变体索引(用于prompt_lab，user和assistant消息)',
    content         text                               not null comment '消息内容',
    responseTimeMs  int                                null comment '响应时间(毫秒)',
    inputTokens     int                                null comment '输入Token数',
    outputTokens    int                                null comment '输出Token数',
    cost            decimal(10, 6)                     null comment '成本(USD)',
    reasoning       text                               null comment '思考过程（thinking模式）',
    codeBlocks      text                               null comment '代码块列表（JSON格式）',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '逻辑删除',
    index idx_conversation (conversationId, messageIndex),
    index idx_model (modelName, isDelete),
    index idx_user (userId, isDelete),
    index idx_variant (variantIndex, isDelete)
) comment '对话消息表' collate = utf8mb4_unicode_ci;

-- 模型信息表（存储从OpenRouter同步的模型列表）
create table if not exists model
(
    id              varchar(100) primary key comment '模型ID（OpenRouter格式，如：openai/gpt-4o）',
    name            varchar(200)                       not null comment '模型显示名称',
    description     text                               null comment '模型描述',
    provider        varchar(100)                       null comment '提供商（如：OpenAI, Anthropic）',
    contextLength   int                                null comment '上下文长度（tokens）',
    inputPrice      decimal(10, 6)                     null comment '输入价格（每百万tokens，美元）',
    outputPrice     decimal(10, 6)                     null comment '输出价格（每百万tokens，美元）',
    recommended     tinyint      default 0             not null comment '是否推荐（1-推荐 0-不推荐）',
    isChina         tinyint      default 0             not null comment '是否国内模型（1-国内 0-国外）',
    tags            varchar(500)                       null comment '标签（JSON数组字符串）',
    rawData         text                               null comment 'OpenRouter原始数据（JSON）',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0             not null comment '逻辑删除',
    index idx_provider (provider, isDelete),
    index idx_recommended (recommended, isDelete),
    index idx_updateTime (updateTime)
) comment '模型信息表' collate = utf8mb4_unicode_ci;

-- 用户评分表
create table if not exists rating
(
    id              varchar(36) primary key comment '评分唯一标识',
    conversationId  varchar(36)                        not null comment '对话ID',
    messageIndex    int                                not null comment '消息序号(对应某一轮对话)',
    userId          bigint                             not null comment '用户ID',
    ratingType      varchar(20)                        not null comment '评分类型: left_better/right_better/tie/both_bad/variant_N',
    winnerModel     varchar(100)                       null comment '获胜模型',
    loserModel      varchar(100)                       null comment '失败模型',
    winnerVariantIndex int                             null comment '获胜变体索引(用于prompt_lab)',
    loserVariantIndex  int                             null comment '失败变体索引(用于prompt_lab)',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '逻辑删除',
    unique key uk_conversation_message_user (conversationId, messageIndex, userId, isDelete),
    index idx_conversation (conversationId, isDelete),
    index idx_user (userId, isDelete),
    index idx_winner (winnerModel, isDelete)
) comment '用户评分表' collate = utf8mb4_unicode_ci;
