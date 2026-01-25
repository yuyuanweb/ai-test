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

  type CodeModeRequest = {
    /** 模型列表（1-8个） */
    models?: string[]
    /** 用户需求描述 */
    prompt?: string
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
    content?: string
    responseTimeMs?: number
    inputTokens?: number
    outputTokens?: number
    cost?: number
    reasoning?: string
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

  type getConversationMessagesParams = {
    conversationId: string
  }

  type getConversationParams = {
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

  type PromptLabRequest = {
    /** 模型名称 */
    model?: string
    /** 提示词变体列表 */
    promptVariants?: string[]
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
    /** 对话ID，多轮对话时传入 */
    conversationId?: string
    /** 是否使用流式响应 */
    stream?: boolean
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
