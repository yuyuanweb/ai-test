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
    conversationType    varchar(20)                        not null comment '对话类型: side_by_side/prompt_lab/battle',
    models              json                               not null comment '参与的模型列表',
    codePreviewEnabled  tinyint  default 0                 not null comment '是否启用代码预览（1-启用 0-不启用）',
    isAnonymous         tinyint  default 0                 not null comment '是否为匿名模式（1-匿名 0-非匿名）',
    modelMapping        json                               null comment '模型匿名映射关系',
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
    totalTokens     bigint       default 0             not null comment '累计使用Token数',
    totalCost       decimal(12, 6) default 0           not null comment '累计花费（美元）',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0             not null comment '逻辑删除',
    index idx_provider (provider, isDelete),
    index idx_recommended (recommended, isDelete),
    index idx_updateTime (updateTime),
    index idx_list (isDelete, isChina, recommended, updateTime)
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

-- 场景表 (阶段5: 场景化批量测试)
create table if not exists scene
(
    id          varchar(36) primary key comment '场景唯一标识',
    userId      bigint                             null comment '创建用户ID(预设场景为NULL)',
    name        varchar(100)                       not null comment '场景名称',
    description text                               null comment '场景描述',
    category    varchar(50)                        null comment '分类:编程/数学/文案等',
    isPreset    tinyint      default 0             not null comment '是否为预设场景(1-预设 0-自定义)',
    isActive    tinyint      default 1             not null comment '是否启用(1-启用 0-禁用)',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint      default 0             not null comment '逻辑删除',
    index idx_category (category, isDelete),
    index idx_user (userId, isDelete),
    index idx_preset (isPreset, isDelete)
) comment '测试场景表' collate = utf8mb4_unicode_ci;
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, '知识问答场景', '测试AI模型的历史、科学、常识问答能力', '问答', 1, 1, '2026-01-25 15:23:26', '2026-01-25 21:03:39', 0);
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('26af3586-d8ea-40e7-9875-dc2a564586d1', 370798160626356224, '逻辑推理场景', '测试AI模型的逻辑题、推理游戏解答能力', '推理', 1, 1, '2026-01-25 15:23:26', '2026-01-25 21:03:39', 0);
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('aef23bb8-820b-41ec-89d4-19a340854a82', 370798160626356224, '文案创作场景', '测试AI模型的文章、广告文案、产品介绍创作能力', '文案', 1, 1, '2026-01-25 15:23:25', '2026-01-25 21:03:39', 0);
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('afce248c-3225-4f09-84a1-65cf1b019e32', 370798160626356224, '数学推理场景', '测试AI模型的算术、代数、几何、应用题解答能力', '数学', 1, 1, '2026-01-25 15:23:25', '2026-01-25 21:03:39', 0);
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('d06c63d1-f66f-4f35-a5b3-070fe46214f6', 370798160626356224, '编程能力场景', '测试AI模型的代码生成、调试、解释、算法实现能力', '编程', 1, 1, '2026-01-25 15:23:25', '2026-01-25 21:03:39', 0);
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('d300e628-e24b-4afb-894a-7a23e2b9fcd2', 370798160626356224, '文本分析场景', '测试AI模型的摘要、情感分析能力', '分析', 1, 1, '2026-01-25 15:23:26', '2026-01-25 21:03:39', 0);
INSERT INTO `scene` (`id`, `userId`, `name`, `description`, `category`, `isPreset`, `isActive`, `createTime`, `updateTime`, `isDelete`) VALUES ('ee4c4cf4-4f61-4f19-944e-6a363000da01', 370798160626356224, '翻译能力场景', '测试AI模型的中英互译、术语翻译能力', '翻译', 1, 1, '2026-01-25 15:23:25', '2026-01-25 21:03:39', 0);
-- 场景提示词表 (阶段5: 场景化批量测试)
create table if not exists scene_prompt
(
    id              varchar(36) primary key comment '提示词唯一标识',
    sceneId         varchar(36)                        not null comment '场景ID',
    userId          bigint                             not null comment '用户ID',
    promptIndex     int                                not null comment '提示词序号',
    title           varchar(200)                       not null comment '提示词标题',
    content         text                               not null comment '提示词内容',
    difficulty      varchar(20)                        null comment '难度: easy/medium/hard',
    tags            json                               null comment '标签数组',
    expectedOutput  text                               null comment '期望输出(可选)',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0             not null comment '逻辑删除',
    index idx_scene (sceneId, promptIndex),
    index idx_user (userId, isDelete)
) comment '场景提示词表' collate = utf8mb4_unicode_ci;
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('0475bc0d-e8c7-4dc2-84cf-c5552717f883', '26af3586-d8ea-40e7-9875-dc2a564586d1', 370798160626356224, 1, '过河问题', '农夫需要把狼、羊、白菜运过河。船每次只能载农夫和其中一样东西。如果农夫不在场，狼会吃羊，羊会吃白菜。请问农夫应该如何安排才能把三样东西都安全运过河？请给出详细步骤。', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('06ebc60b-12aa-4c07-ab85-1a0f86d807a1', 'd300e628-e24b-4afb-894a-7a23e2b9fcd2', 370798160626356224, 1, '情感分析', '请分析以下产品评论的情感倾向（正面/负面/中性），并说明理由：\n\n\"这款手机的外观设计非常漂亮，拍照效果也很出色。但是电池续航能力一般，而且价格偏贵。总体来说，如果预算充足的话，还是值得购买的。\"', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('2d82599e-8c9c-4ca6-b69c-81c18d463d88', 'afce248c-3225-4f09-84a1-65cf1b019e32', 370798160626356224, 3, '几何问题', '在直角三角形ABC中，角C为直角，AB=10cm，AC=6cm。求BC的长度和三角形的面积。', 'easy', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('3258e182-ecd5-4d14-bb1c-f47502c9de93', 'ee4c4cf4-4f61-4f19-944e-6a363000da01', 370798160626356224, 3, '技术文档翻译（英译中）', '请将以下技术文档翻译成中文：\n\nThe API uses RESTful architecture and supports both JSON and XML data formats. Authentication is required for all endpoints using OAuth 2.0 protocol. Rate limiting is set to 1000 requests per hour per API key.', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('35c0b4fa-abe4-4543-80b2-1e42df23e97f', 'd300e628-e24b-4afb-894a-7a23e2b9fcd2', 370798160626356224, 0, '新闻摘要', '请为以下新闻生成100字以内的摘要：\n\n据报道，某科技公司今日发布了其最新的人工智能芯片，该芯片采用5纳米制程工艺，集成了超过500亿个晶体管。与上一代产品相比，新芯片的AI运算性能提升了3倍，能效比提升了2倍，同时功耗降低了40%。该芯片将主要应用于数据中心、自动驾驶和边缘计算等领域。公司CEO表示，这款芯片的发布标志着公司在AI芯片领域取得了重大突破，将推动人工智能技术的普及和应用。', 'easy', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('38f8e4d8-88a3-45bc-a5a9-16ffd7842f47', 'd06c63d1-f66f-4f35-a5b3-070fe46214f6', 370798160626356224, 0, '实现快速排序算法', '请用Python实现快速排序算法，要求：\n1. 函数名为 quick_sort\n2. 参数为一个整数列表\n3. 返回排序后的列表\n4. 包含注释说明', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('443afe1a-d422-4eff-a465-2edf8c730e0b', 'd300e628-e24b-4afb-894a-7a23e2b9fcd2', 370798160626356224, 2, '文章主题提取', '请阅读以下段落，提取出3-5个核心主题词：\n\n随着全球气候变化的加剧，可再生能源的发展变得日益重要。太阳能和风能作为清洁能源的代表，正在世界各地得到广泛应用。许多国家纷纷制定政策，鼓励企业和个人使用可再生能源，减少对化石燃料的依赖。同时，储能技术的进步也为可再生能源的规模化应用提供了保障。专家预测，到2050年，可再生能源将占全球能源消费的一半以上。', 'easy', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('489181a1-dd6f-4d6f-8a50-1c87524c862a', 'ee4c4cf4-4f61-4f19-944e-6a363000da01', 370798160626356224, 0, '科技新闻翻译（英译中）', '请将以下英文科技新闻翻译成中文：\n\nArtificial intelligence is rapidly transforming the healthcare industry. Machine learning algorithms can now detect diseases from medical images with accuracy comparable to human experts. This breakthrough technology promises to improve diagnostic speed and reduce healthcare costs globally.', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('4b84b176-6746-43bc-80cf-f6e0951bc609', 'aef23bb8-820b-41ec-89d4-19a340854a82', 370798160626356224, 3, '旅游景点推广文案', '为九寨沟风景区撰写推广文案，要求：\n1. 突出景区的自然美景和独特魅力\n2. 包含最佳旅游季节推荐\n3. 字数300字左右\n4. 激发读者的旅游欲望', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('6540b16b-2fbe-4293-88f8-317b564c90c4', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 0, '中国历史问题', '请简要介绍秦始皇统一六国的历史意义，以及秦朝建立的主要制度。', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('66a4889d-995f-4f42-8ef6-f5fdca41f0c5', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 9, '医学常识', '什么是免疫系统？它是如何保护我们的身体免受疾病侵害的？请解释抗体的作用。', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('6d831eb3-9e94-4644-a936-4587b9bcc3b6', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 7, '天文学知识', '黑洞是如何形成的？为什么连光都无法逃脱黑洞？请用通俗的语言解释。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('7f0176cf-1331-472e-82df-995084f97d27', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 1, '物理学知识', '什么是量子纠缠？请用通俗易懂的语言解释这个现象，并举一个实际应用的例子。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('8546380b-c8a9-41f2-9e4e-07ccaad75e73', 'ee4c4cf4-4f61-4f19-944e-6a363000da01', 370798160626356224, 1, '古诗词翻译（中译英）', '请将以下李白的诗句翻译成英文，要求保持诗意和韵味：\n\n床前明月光，疑是地上霜。\n举头望明月，低头思故乡。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('8bd3fa8c-a8a1-4043-bad2-19cfda13cc3b', '26af3586-d8ea-40e7-9875-dc2a564586d1', 370798160626356224, 3, '真假话问题', '有三个人：A、B、C。其中一个只说真话，一个只说假话，一个有时说真话有时说假话。\nA说：\"B是说假话的人\"\nB说：\"C不是有时说真话有时说假话的人\"\nC说：\"A是说真话的人\"\n请推理出谁是只说真话的人、谁是只说假话的人、谁是有时说真话有时说假话的人。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('8f50fd6b-a3fc-4304-85d1-181b03154bf2', 'aef23bb8-820b-41ec-89d4-19a340854a82', 370798160626356224, 4, '企业品牌故事', '为一家环保科技公司撰写品牌故事，要求：\n1. 讲述公司创立初心和发展历程\n2. 体现企业的社会责任感\n3. 字数500字左右\n4. 语言真诚且富有感染力', 'hard', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('8f7a68a0-f669-4d0d-a504-3569f0a8f57b', 'afce248c-3225-4f09-84a1-65cf1b019e32', 370798160626356224, 1, '求解二次方程', '求解方程 2x² - 5x + 2 = 0，请给出详细的求解步骤，包括判别式的计算和根的公式应用。', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('900767a3-4465-472e-b54b-c25f9e569a42', '26af3586-d8ea-40e7-9875-dc2a564586d1', 370798160626356224, 2, '数字推理', '找出数列的规律并填写空缺的数字：2, 5, 10, 17, 26, ?, 50。请说明推理过程。', 'easy', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('98a508e0-8c07-4b10-b3be-d0d0240ae187', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 3, '地理知识', '为什么地球会有四季变化？请从地球公转和地轴倾斜的角度进行解释。', 'easy', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('9a8272d3-76d7-4585-af71-4202d089467f', 'ee4c4cf4-4f61-4f19-944e-6a363000da01', 370798160626356224, 2, '商务邮件翻译（中译英）', '请将以下商务邮件翻译成英文：\n\n尊敬的Smith先生，\n\n感谢您对我们产品的关注。关于您询问的技术规格问题，我们的工程师团队已经准备好详细的技术文档。我们建议下周二安排一次线上会议，详细讨论您的需求。\n\n期待您的回复。\n\n此致\n敬礼', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('9cbea2b6-e2a0-4bae-8d06-9815fe96eb9a', 'afce248c-3225-4f09-84a1-65cf1b019e32', 370798160626356224, 4, '数列求和', '求等差数列 2, 5, 8, 11, ... 的前50项和。请说明使用的公式并给出计算过程。', 'easy', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('a9c04fc9-1ba2-4db4-8790-987b29d91786', 'd06c63d1-f66f-4f35-a5b3-070fe46214f6', 370798160626356224, 3, '实现单例模式', '请用Python实现线程安全的单例模式，要求：\n1. 使用装饰器或元类实现\n2. 确保线程安全\n3. 提供使用示例\n4. 解释实现原理', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('ab8a4c4e-bd3f-4808-930b-e78ea9406564', 'd06c63d1-f66f-4f35-a5b3-070fe46214f6', 370798160626356224, 2, '实现LRU缓存', '请实现一个LRU（最近最少使用）缓存机制，要求：\n1. 支持get和put操作\n2. 时间复杂度O(1)\n3. 使用你熟悉的编程语言\n4. 包含详细注释', 'hard', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('b0bffac9-fbbd-4b0f-966d-163e9cb56f28', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 2, '生物学问题', '请解释光合作用的过程，包括光反应和暗反应的主要步骤和产物。', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('b439414d-31af-4df3-bc17-57db52b89236', '26af3586-d8ea-40e7-9875-dc2a564586d1', 370798160626356224, 0, '经典帽子问题', '有3个人，每人头上戴着一顶帽子，帽子有红色和蓝色两种。每个人只能看到其他人的帽子，看不到自己的。现在告诉他们，至少有一顶红色帽子。然后问他们是否知道自己帽子的颜色。第一个人说不知道，第二个人说不知道，第三个人说知道。请问第三个人的帽子是什么颜色？请给出详细的推理过程。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('b49397dd-e495-4c90-91c1-d2aa650f1205', 'd06c63d1-f66f-4f35-a5b3-070fe46214f6', 370798160626356224, 1, '实现二叉树遍历', '请用Java实现二叉树的前序、中序、后序遍历，要求：\n1. 使用递归方式实现\n2. 定义TreeNode类\n3. 包含完整的代码和注释', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('ba29f88f-2142-4b2d-843e-50c4e0fa4049', 'd300e628-e24b-4afb-894a-7a23e2b9fcd2', 370798160626356224, 3, '长文本摘要', '请为以下技术文章生成200字左右的摘要，保留核心技术要点：\n\n区块链技术是一种分布式账本技术，它通过密码学方法将数据区块按时间顺序连接成链式结构，从而创建了一个不可篡改的数据记录系统。每个区块包含一批交易记录、时间戳和前一个区块的哈希值。这种设计使得区块链具有去中心化、透明性和安全性等特点。\n\n区块链的共识机制是其核心技术之一，常见的共识算法包括工作量证明（PoW）、权益证明（PoS）和委托权益证明（DPoS）等。不同的共识机制在性能、安全性和能耗方面各有特点。\n\n目前，区块链技术已经在金融、供应链、医疗、版权保护等多个领域得到应用。智能合约作为区块链的重要应用，实现了合约的自动执行，大大提高了交易效率并降低了成本。然而，区块链技术仍面临着可扩展性、隐私保护和监管等挑战，需要持续的技术创新来解决这些问题。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('bd17dc35-6ed3-4a8b-b4ba-2505f15b3bb0', 'afce248c-3225-4f09-84a1-65cf1b019e32', 370798160626356224, 0, '鸡兔同笼问题', '鸡兔同笼，共有头35个，脚94只。问鸡和兔各有多少只？请给出详细的解题过程。', 'easy', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('c15863cc-67d2-46a8-83ec-47ed92b47015', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 6, '环境科学', '全球变暖的主要原因是什么？温室效应是如何产生的？我们个人可以做些什么来减缓全球变暖？', 'easy', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('c70fd5ba-35ac-42e0-a4ae-c644e018d9cd', 'afce248c-3225-4f09-84a1-65cf1b019e32', 370798160626356224, 2, '概率计算', '一个袋子里有5个红球和3个蓝球，从中随机取出2个球，求取出的2个球颜色相同的概率。请给出详细计算过程。', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('c76cce41-5e22-4d33-9e66-7fba71008bc6', '26af3586-d8ea-40e7-9875-dc2a564586d1', 370798160626356224, 4, '称重问题', '有12个外观相同的球，其中11个重量相同，只有1个球的重量不同（可能更重或更轻）。现在有一个天平，最少称几次可以找出这个不同重量的球，并判断它是更重还是更轻？请给出详细的称重策略。', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('cb78a2b0-837c-44bc-96be-3f40dc3e2dcd', 'aef23bb8-820b-41ec-89d4-19a340854a82', 370798160626356224, 1, '咖啡店产品介绍', '为一家精品咖啡店的招牌拿铁咖啡撰写产品介绍，要求：\n1. 描述咖啡的口感、香气、制作工艺\n2. 体现咖啡店的品质和特色\n3. 字数200字左右\n4. 语言优雅且有吸引力', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('cd106317-a899-419e-a8e7-faf38ec2da69', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 8, '文学常识', '请介绍中国四大名著及其作者，并简要说明每部作品的主题和文学价值。', 'easy', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('ce4cf452-3352-4dc7-906b-69552aa36e96', 'ee4c4cf4-4f61-4f19-944e-6a363000da01', 370798160626356224, 4, '文学作品翻译（英译中）', '请将莎士比亚的经典名句翻译成中文，要求优美流畅：\n\nTo be, or not to be, that is the question: Whether \'tis nobler in the mind to suffer the slings and arrows of outrageous fortune, or to take arms against a sea of troubles.', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('cf48852a-129c-49b9-b291-164008209377', 'd300e628-e24b-4afb-894a-7a23e2b9fcd2', 370798160626356224, 4, '多维度情感分析', '请从产品质量、服务态度、性价比三个维度分析以下酒店评论的情感：\n\n\"这次入住体验总体不错。房间很干净，装修也很新，床很舒服。前台服务人员态度很好，check-in和check-out都很快。不过价格确实有点贵，早餐种类也不够丰富。如果能优惠一些就更好了。下次如果有活动价格会考虑再来。\"', 'hard', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('d2b6ff49-44d6-4996-b2ac-eb40d91b3515', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 4, '经济学常识', '什么是通货膨胀？它对普通老百姓的生活有什么影响？政府通常采取什么措施来控制通货膨胀？', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('d999a073-a9d6-476d-89e5-2a8dd56fabeb', '10fcb9d9-16ca-4cbf-aa69-b522d3112dcd', 370798160626356224, 5, '世界历史', '请简要介绍第二次世界大战的起因、主要战役和历史影响。', 'medium', NULL, NULL, '2026-01-25 15:23:26', '2026-01-25 15:23:26', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('f69fd3eb-88e8-4df9-b753-2bce16394912', 'aef23bb8-820b-41ec-89d4-19a340854a82', 370798160626356224, 2, '科技博客文章', '撰写一篇关于\"人工智能在医疗领域的应用\"的科技博客文章，要求：\n1. 字数800字左右\n2. 包含引言、主体、结论\n3. 介绍2-3个具体应用案例\n4. 语言专业但易懂', 'hard', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('f71ae94d-7be0-4d3f-8b0e-57c438eaf14d', 'aef23bb8-820b-41ec-89d4-19a340854a82', 370798160626356224, 0, '智能手机广告文案', '为一款新上市的5G智能手机撰写广告文案，要求：\n1. 突出产品核心卖点（性能、拍照、续航）\n2. 字数控制在100字以内\n3. 语言简洁有力，富有感染力\n4. 包含一句广告语', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
INSERT INTO `scene_prompt` (`id`, `sceneId`, `userId`, `promptIndex`, `title`, `content`, `difficulty`, `tags`, `expectedOutput`, `createTime`, `updateTime`, `isDelete`) VALUES ('f8361af8-5f01-45c4-b520-64fae176ed7f', 'd06c63d1-f66f-4f35-a5b3-070fe46214f6', 370798160626356224, 4, '实现REST API', '请用Node.js和Express实现一个简单的用户管理REST API，要求：\n1. 包含GET、POST、PUT、DELETE接口\n2. 使用内存数据存储\n3. 包含输入验证\n4. 提供完整可运行的代码', 'medium', NULL, NULL, '2026-01-25 15:23:25', '2026-01-25 15:23:25', 0);
-- 批量测试任务表 (阶段5: 场景化批量测试)
create table if not exists test_task
(
    id                  varchar(36) primary key comment '任务唯一标识',
    userId              bigint                             not null comment '用户ID',
    name                varchar(200)                       null comment '任务名称',
    sceneId             varchar(36)                        not null comment '场景ID',
    models              json                               not null comment '测试的模型列表',
    status              varchar(20)                         not null comment '状态: pending/running/completed/failed/cancelled',
    config json DEFAULT NULL COMMENT '任务配置参数(JSON格式，包含temperature、topP等)',
    totalSubtasks       int      default 0                 not null comment '子任务总数',
    completedSubtasks   int      default 0                 not null comment '已完成子任务数',
    startedAt           datetime                           null comment '开始时间',
    completedAt         datetime                           null comment '完成时间',
    createTime          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime          datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete            tinyint  default 0                 not null comment '逻辑删除',
    index idx_user_created (userId, createTime desc, isDelete),
    index idx_status (status, isDelete),
    index idx_scene (sceneId, isDelete)
) comment '批量测试任务表' collate = utf8mb4_unicode_ci;

-- 批量测试结果表 (阶段5: 场景化批量测试)
create table if not exists test_result
(
    id              varchar(36) primary key comment '结果唯一标识',
    taskId           varchar(36)                        not null comment '任务ID',
    userId           bigint                             not null comment '用户ID',
    sceneId          varchar(36)                        not null comment '场景ID',
    promptId         varchar(36)                        not null comment '提示词ID',
    modelName        varchar(100)                       not null comment '模型名称',
    inputPrompt      text                               not null comment '输入提示词',
    outputText       text                               not null comment '输出内容',
    reasoning        text                               null comment '思考过程内容',
    responseTimeMs   int                                null comment '响应时间(毫秒)',
    inputTokens      int                                null comment '输入Token数',
    outputTokens     int                                null comment '输出Token数',
    cost             decimal(10, 6)                     null comment '成本(USD)',
    userRating       int                                null comment '用户评分(1-5)',
    aiScore          json                               null comment 'AI评分详情(多个评委模型的评分)',
    createTime       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '逻辑删除',
    index idx_task (taskId, isDelete),
    index idx_task_create (taskId, createTime),
    index idx_model (modelName, isDelete),
    index idx_user (userId, isDelete),
    index idx_scene (sceneId, isDelete),
    index idx_prompt (promptId, isDelete)
) comment '批量测试结果表' collate = utf8mb4_unicode_ci;

-- 用户-模型使用统计表
create table if not exists user_model_usage
(
    id              varchar(36) primary key comment '记录唯一标识',
    userId          bigint                             not null comment '用户ID',
    modelName       varchar(100)                       not null comment '模型名称',
    totalTokens     bigint       default 0             not null comment '累计使用Token数',
    totalCost       decimal(12, 6) default 0           not null comment '累计花费（美元）',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0             not null comment '逻辑删除',
    unique key uk_user_model (userId, modelName, isDelete),
    index idx_user (userId, isDelete),
    index idx_model (modelName, isDelete)
) comment '用户-模型使用统计表' collate = utf8mb4_unicode_ci;

-- 提示词模板表 (阶段8: 提示词模板库)
create table if not exists prompt_template
(
    id              varchar(36) primary key comment '模板唯一标识',
    userId          bigint                             null comment '用户ID(预设模板为NULL)',
    name            varchar(100)                       not null comment '模板名称',
    description     text                               null comment '模板描述',
    strategy        varchar(50)                        not null comment '策略类型: direct/cot/role_play/few_shot',
    content         text                               not null comment '模板内容(支持占位符)',
    variables       json                               null comment '变量列表(JSON数组)',
    category        varchar(50)                        null comment '分类',
    isPreset        tinyint      default 0             not null comment '是否为预设模板(1-预设 0-自定义)',
    usageCount      int          default 0             not null comment '使用次数',
    isActive        tinyint      default 1             not null comment '是否启用(1-启用 0-禁用)',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0             not null comment '逻辑删除',
    index idx_user (userId, isDelete),
    index idx_strategy (strategy, isDelete),
    index idx_preset (isPreset, isDelete),
    index idx_category (category, isDelete),
    index idx_list_pt (isDelete, isPreset, usageCount, createTime)
) comment '提示词模板表' collate = utf8mb4_unicode_ci;

-- 初始化预设模板数据
INSERT INTO prompt_template (id, userId, name, description, strategy, content, variables, category, isPreset, usageCount, isActive, isDelete) VALUES
('preset-direct-001', NULL, '直接提问模板', '适用于简单直接的问答场景', 'direct', '{question}', '["question"]', '通用', 1, 0, 1, 0),
('preset-cot-001', NULL, 'CoT思维链模板', '引导AI进行逐步思考', 'cot', '请逐步思考以下问题，并给出详细的分析过程：

问题：{question}

请按照以下步骤思考：
1. 理解问题
2. 分析关键信息
3. 推理过程
4. 得出结论', '["question"]', '通用', 1, 0, 1, 0),
('preset-role-001', NULL, '角色扮演模板', '让AI扮演特定角色', 'role_play', '你是一位{role}，请以{role}的身份回答以下问题：

问题：{question}

请以专业、准确的方式回答。', '["role", "question"]', '通用', 1, 0, 1, 0),
('preset-fewshot-001', NULL, 'Few-shot示例模板', '通过示例引导AI理解任务', 'few_shot', '以下是几个示例：

示例1：
输入：{example1_input}
输出：{example1_output}

示例2：
输入：{example2_input}
输出：{example2_output}

现在请根据以上示例，回答以下问题：
输入：{question}
输出：', '["example1_input", "example1_output", "example2_input", "example2_output", "question"]', '通用', 1, 0, 1, 0);
