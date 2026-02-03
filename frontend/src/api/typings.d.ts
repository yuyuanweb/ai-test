declare namespace API {
  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseConversation = {
    code?: number
    data?: Conversation
    message?: string
  }

  type BaseResponseListConversationMessage = {
    code?: number
    data?: ConversationMessage[]
    message?: string
  }

  type BaseResponseListModelVO = {
    code?: number
    data?: ModelVO[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseMapStringObject = {
    code?: number
    data?: Record<string, any>
    message?: string
  }

  type BaseResponsePageConversation = {
    code?: number
    data?: PageConversation
    message?: string
  }

  type BaseResponsePageModelVO = {
    code?: number
    data?: PageModelVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponsePromptOptimizationVO = {
    code?: number
    data?: PromptOptimizationVO
    message?: string
  }

  type BaseResponseRatingVO = {
    code?: number
    data?: RatingVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseBattleModelMappingVO = {
    code?: number
    data?: BattleModelMappingVO
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type BaseResponseListGeneratedImageVO = {
    code?: number
    data?: GeneratedImageVO[]
    message?: string
  }

  type CodeModeRequest = {
    /** 模型列表（1-8个） */
    models?: string[]
    /** 用户需求描述 */
    prompt?: string
    /** 图片URL列表（可选，用于多模态） */
    imageUrls?: string[]
    /** 对话ID，多轮对话时传入 */
    conversationId?: string
    /** 是否使用流式响应 */
    stream?: boolean
  }

  type Conversation = {
    id?: string
    userId?: number
    title?: string
    conversationType?: string
    models?: string
    codePreviewEnabled?: boolean
    isAnonymous?: boolean
    modelMapping?: string
    totalTokens?: number
    totalCost?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ConversationMessage = {
    id?: string
    conversationId?: string
    userId?: number
    messageIndex?: number
    role?: string
    modelName?: string
    /** 图片URL列表（JSON字符串） */
    images?: string
    content?: string
    responseTimeMs?: number
    inputTokens?: number
    outputTokens?: number
    cost?: number
    reasoning?: string
    /** 工具使用信息（JSON字符串） */
    toolsUsed?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type CreateConversationRequest = {
    /** 对话标题 */
    title?: string
    /** 对话类型 */
    conversationType?: string
    /** 参与的模型列表 */
    models?: string[]
  }

  type deleteRatingParams = {
    conversationId: string
    messageIndex: number
  }

  type DeleteRequest = {
    id?: number
  }

  type DeleteConversationRequest = {
    id?: string
  }

  type getConversationMessagesParams = {
    conversationId: string
  }

  type getConversationParams = {
    conversationId: string
  }

  type getBattleModelMappingParams = {
    conversationId: string
  }

  type getRatingParams = {
    conversationId: string
    messageIndex: number
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type listConversationsParams = {
    pageNum?: number
    pageSize?: number
    codePreviewEnabled?: number
  }

  type LoginUserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type ModelQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    /** 搜索关键词 */
    searchText?: string
    /** 提供商 */
    provider?: string
    /** 是否只查询推荐模型 */
    onlyRecommended?: boolean
    /** 是否只查询国内模型 */
    onlyChina?: boolean
    /** 是否只查询支持图片生成的模型 */
    onlySupportsImageGen?: boolean
    /** 是否只查询支持多模态的模型 */
    onlySupportsMultimodal?: boolean
  }

  type ModelVO = {
    /** 模型ID */
    id?: string
    /** 模型显示名称 */
    name?: string
    /** 模型描述 */
    description?: string
    /** 上下文长度(tokens) */
    contextLength?: number
    /** 输入价格(每百万tokens，美元) */
    inputPrice?: number
    /** 输出价格(每百万tokens，美元) */
    outputPrice?: number
    /** 提供商 */
    provider?: string
    /** 是否推荐 */
    recommended?: boolean
    /** 是否国内模型 */
    isChina?: boolean
    /** 是否支持多模态(图片) */
    supportsMultimodal?: boolean
    /** 是否支持图片生成 */
    supportsImageGen?: boolean
    /** 是否支持工具调用(联网搜索) */
    supportsToolCalling?: boolean
    /** 能力标签 */
    tags?: string[]
    /** 累计使用Token数 */
    totalTokens?: number
    /** 累计花费（美元） */
    totalCost?: number
    /** 用户累计使用Token数 */
    userTotalTokens?: number
    /** 用户累计花费（美元） */
    userTotalCost?: number
  }

  type PageConversation = {
    records?: Conversation[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageModelVO = {
    records?: ModelVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PromptOptimizationRequest = {
    originalPrompt?: string
    aiResponse?: string
    evaluationModel?: string
  }

  type PromptOptimizationVO = {
    issues?: string[]
    optimizedPrompt?: string
    improvements?: string[]
  }

  type PromptLabRequest = {
    /** 模型名称 */
    model?: string
    /** 提示词变体列表 */
    promptVariants?: string[]
    /** 变体图片URL列表（与promptVariants一一对应） */
    variantImageUrls?: string[][]
    /** 对话ID，多轮对话时传入 */
    conversationId?: string
    /** 是否使用流式响应 */
    stream?: boolean
  }

  type RatingAddRequest = {
    conversationId?: string
    messageIndex?: number
    ratingType?: string
    winnerModel?: string
    loserModel?: string
    winnerVariantIndex?: number
    loserVariantIndex?: number
  }

  type RatingVO = {
    id?: string
    conversationId?: string
    messageIndex?: number
    userId?: number
    ratingType?: string
    winnerModel?: string
    loserModel?: string
    winnerVariantIndex?: number
    loserVariantIndex?: number
    createTime?: string
  }

  type ServerSentEventStreamChunkVO = true

  type SideBySideRequest = {
    /** 模型列表(1-8个) */
    models?: string[]
    /** 用户提示词 */
    prompt?: string
    /** 图片URL列表（可选，用于多模态） */
    imageUrls?: string[]
    /** 对话ID，多轮对话时传入 */
    conversationId?: string
    /** 是否使用流式响应 */
    stream?: boolean
    /** 是否启用联网搜索 */
    webSearchEnabled?: boolean
  }

  type BattleRequest = {
    /** 模型列表(2-8个) */
    models?: string[]
    /** 用户提示词 */
    prompt?: string
    /** 对话ID，多轮对话时传入 */
    conversationId?: string
    /** 是否使用流式响应 */
    stream?: boolean
  }

  type BattleModelMappingVO = {
    /** 匿名标识到真实模型名称的映射 */
    mapping?: Record<string, string>
  }

  type UploadImageVO = {
    /** 可访问URL */
    url?: string
    /** 原始文件名 */
    originalFilename?: string
    /** 文件大小（字节） */
    size?: number
    /** Content-Type */
    contentType?: string
  }

  type BaseResponseUploadImageVO = {
    code?: number
    data?: UploadImageVO
    message?: string
  }

  type GenerateImageRequest = {
    /** 模型名称（OpenRouter 模型 ID，匿名模式下可为空） */
    model?: string
    /** 图片生成提示词 */
    prompt?: string
    /** 参考图片 URL 列表（可选，用于图生图） */
    referenceImageUrls?: string[]
    /** 生成图片数量（1-4） */
    count?: number
    /** 对话ID（可选） */
    conversationId?: string
    /** 模型列表（可选，用于创建会话时指定模型） */
    models?: string[]
    /** 会话类型（side_by_side / prompt_lab / battle） */
    conversationType?: string
    /** 变体索引（0, 1, 2...） */
    variantIndex?: number
    /** 消息索引（用于多变体共享同一个消息索引） */
    messageIndex?: number
    /** 是否匿名模式（Battle 页面使用） */
    isAnonymous?: boolean
  }

  type GeneratedImageVO = {
    /** 图片访问地址 */
    url?: string
    /** 模型名称（OpenRouter 模型 ID） */
    modelName?: string
    /** 生成序号（从 0 开始） */
    index?: number
    /** 输入 Token 数 */
    inputTokens?: number
    /** 输出 Token 数 */
    outputTokens?: number
    /** 总 Token 数 */
    totalTokens?: number
    /** 本次调用费用（USD） */
    cost?: number
    /** 会话ID */
    conversationId?: string
    /** 消息索引 */
    messageIndex?: number
  }

  type ImageStreamChunkVO = {
    /** 事件类型：thinking/image/done/error */
    type?: string
    /** 思考内容 */
    thinking?: string
    /** 完整思考内容 */
    fullThinking?: string
    /** 生成的图片信息 */
    image?: GeneratedImageVO
    /** 会话ID */
    conversationId?: string
    /** 消息索引 */
    messageIndex?: number
    /** 变体索引 */
    variantIndex?: number
    /** 模型名称 */
    modelName?: string
    /** 错误信息 */
    error?: string
  }

  type testAiSimpleParams = {
    prompt: string
  }

  type testAiStreamParams = {
    prompt: string
    model?: string
  }

  type testMultiStreamParams = {
    prompt: string
    models: string[]
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
