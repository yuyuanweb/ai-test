<template>
  <div class="arena-container">
    <!-- 左侧边栏 -->
    <aside class="left-sidebar">
      <!-- Logo -->
      <div class="logo-section">
        <img src="@/assets/logo.png" alt="Logo" class="logo-img" />
        <h2 class="app-name">大模型测试平台</h2>
      </div>

      <!-- 菜单按钮 -->
      <nav class="nav-buttons">
        <button class="nav-btn" @click="handleNewChat">
          <EditOutlined />
          <span>新对话</span>
        </button>
        <button class="nav-btn" @click="router.push('/model/manage')">
          <AppstoreOutlined />
          <span>模型管理</span>
        </button>
        <button class="nav-btn" @click="router.push('/batch-test/create')">
          <ThunderboltOutlined />
          <span>批量测试</span>
        </button>
        <button class="nav-btn" @click="router.push('/batch-test/list')">
          <UnorderedListOutlined />
          <span>测试任务</span>
        </button>
        <button class="nav-btn" @click="router.push('/prompt-template/manage')">
          <FileTextOutlined />
          <span>提示词模板</span>
        </button>
        <button class="nav-btn" @click="router.push('/statistics')">
          <BarChartOutlined />
          <span>数据分析</span>
        </button>
      </nav>

      <!-- 历史对话 -->
      <div class="history-area" @scroll="handleScroll" ref="historyAreaRef">
        <!-- Today -->
        <div v-if="todayConversations.length > 0" class="history-group">
          <div class="group-label">今天</div>
          <div
            v-for="conv in todayConversations"
            :key="conv.id"
            class="history-item"
            @click="openConversation(conv.id, conv.conversationType, conv.codePreviewEnabled)"
          >
            <!-- 根据会话类型显示不同的图标（左侧，固定宽度区域内右对齐） -->
            <div v-if="conv.conversationType === 'prompt_lab'" class="conversation-type-icon">
              <ExperimentOutlined style="font-size: 12px; color: #8b5cf6;" />
            </div>
            <div v-else-if="conv.conversationType === 'battle'" class="conversation-type-icon">
              <TrophyOutlined style="font-size: 12px; color: #ff9800;" />
            </div>
            <div v-else class="model-logos">
              <template v-for="(logo, idx) in getModelLogos(conv.models)" :key="idx">
                <img
                  v-if="!logo.isMore"
                  :src="logo.url"
                  :alt="logo.alt"
                  class="model-logo"
                />
                <a-popover v-else placement="right" trigger="hover">
                  <template #content>
                    <div class="all-models-popup">
                      <div 
                        v-for="(allLogo, allIdx) in getAllModelLogos(conv.models)" 
                        :key="allIdx"
                        class="popup-model-item"
                      >
                        <img :src="allLogo.url" :alt="allLogo.alt" class="popup-model-logo" />
                        <span class="popup-model-name">{{ allLogo.name }}</span>
                      </div>
                    </div>
                  </template>
                  <span class="more-models">
                    {{ logo.alt }}
                  </span>
                </a-popover>
              </template>
            </div>
            
            <!-- 标题文字在右侧（左对齐） -->
            <span class="history-title">{{ conv.title || '无标题对话' }}</span>
            <a-popconfirm
              title="确定要删除这个会话吗？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDeleteConversation(conv.id, $event)"
            >
              <button
                class="delete-btn"
                title="删除会话"
                @click.stop
              >
                <DeleteOutlined />
              </button>
            </a-popconfirm>
          </div>
        </div>

        <!-- Yesterday -->
        <div v-if="yesterdayConversations.length > 0" class="history-group">
          <div class="group-label">昨天</div>
          <div
            v-for="conv in yesterdayConversations"
            :key="conv.id"
            class="history-item"
            @click="openConversation(conv.id, conv.conversationType, conv.codePreviewEnabled)"
          >
            <!-- 根据会话类型显示不同的图标（左侧，固定宽度区域内右对齐） -->
            <div v-if="conv.conversationType === 'prompt_lab'" class="conversation-type-icon">
              <ExperimentOutlined style="font-size: 12px; color: #8b5cf6;" />
            </div>
            <div v-else-if="conv.conversationType === 'battle'" class="conversation-type-icon">
              <TrophyOutlined style="font-size: 12px; color: #ff9800;" />
            </div>
            <div v-else class="model-logos">
              <template v-for="(logo, idx) in getModelLogos(conv.models)" :key="idx">
                <img
                  v-if="!logo.isMore"
                  :src="logo.url"
                  :alt="logo.alt"
                  class="model-logo"
                />
                <a-popover v-else placement="right" trigger="hover">
                  <template #content>
                    <div class="all-models-popup">
                      <div 
                        v-for="(allLogo, allIdx) in getAllModelLogos(conv.models)" 
                        :key="allIdx"
                        class="popup-model-item"
                      >
                        <img :src="allLogo.url" :alt="allLogo.alt" class="popup-model-logo" />
                        <span class="popup-model-name">{{ allLogo.name }}</span>
                      </div>
                    </div>
                  </template>
                  <span class="more-models">
                    {{ logo.alt }}
                  </span>
                </a-popover>
              </template>
            </div>
            
            <!-- 标题文字在右侧（左对齐） -->
            <span class="history-title">{{ conv.title || '无标题对话' }}</span>
            <a-popconfirm
              title="确定要删除这个会话吗？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDeleteConversation(conv.id, $event)"
            >
              <button
                class="delete-btn"
                title="删除会话"
                @click.stop
              >
                <DeleteOutlined />
              </button>
            </a-popconfirm>
          </div>
        </div>

        <!-- Older -->
        <div v-if="olderConversations.length > 0" class="history-group">
          <div class="group-label">更早</div>
          <div
            v-for="conv in olderConversations"
            :key="conv.id"
            class="history-item"
            @click="openConversation(conv.id, conv.conversationType, conv.codePreviewEnabled)"
          >
            <!-- 根据会话类型显示不同的图标（左侧，固定宽度区域内右对齐） -->
            <div v-if="conv.conversationType === 'prompt_lab'" class="conversation-type-icon">
              <ExperimentOutlined style="font-size: 12px; color: #8b5cf6;" />
            </div>
            <div v-else-if="conv.conversationType === 'battle'" class="conversation-type-icon">
              <TrophyOutlined style="font-size: 12px; color: #ff9800;" />
            </div>
            <div v-else class="model-logos">
              <template v-for="(logo, idx) in getModelLogos(conv.models)" :key="idx">
                <img
                  v-if="!logo.isMore"
                  :src="logo.url"
                  :alt="logo.alt"
                  class="model-logo"
                />
                <a-popover v-else placement="right" trigger="hover">
                  <template #content>
                    <div class="all-models-popup">
                      <div 
                        v-for="(allLogo, allIdx) in getAllModelLogos(conv.models)" 
                        :key="allIdx"
                        class="popup-model-item"
                      >
                        <img :src="allLogo.url" :alt="allLogo.alt" class="popup-model-logo" />
                        <span class="popup-model-name">{{ allLogo.name }}</span>
                      </div>
                    </div>
                  </template>
                  <span class="more-models">
                    {{ logo.alt }}
                  </span>
                </a-popover>
              </template>
            </div>
            
            <!-- 标题文字在右侧（左对齐） -->
            <span class="history-title">{{ conv.title || '无标题对话' }}</span>
            <a-popconfirm
              title="确定要删除这个会话吗？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDeleteConversation(conv.id, $event)"
            >
              <button
                class="delete-btn"
                title="删除会话"
                @click.stop
              >
                <DeleteOutlined />
              </button>
            </a-popconfirm>
          </div>
        </div>

        <!-- 加载更多提示 -->
        <div v-if="loading && currentPage > 1" class="loading-more">
          <a-spin size="small" />
          <span style="margin-left: 8px; font-size: 12px; color: #9ca3af;">加载更多...</span>
        </div>
        
        <!-- 没有更多提示 -->
        <div v-if="!hasMore && (todayConversations.length + yesterdayConversations.length + olderConversations.length) > 0" class="no-more">
          <span style="font-size: 11px; color: #9ca3af;">没有更多对话了</span>
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && !todayConversations.length && !yesterdayConversations.length && !olderConversations.length">
          <div class="group-label">今天</div>
          <div class="group-label">昨天</div>
          <div class="group-label">更早</div>
        </div>
      </div>

      <!-- 用户区域 -->
      <div class="user-area">
        <!-- 已登录：显示用户信息+退出菜单 -->
        <a-dropdown v-if="loginUser.id" :trigger="['click']" @visibleChange="handleDropdownVisibleChange">
          <div class="user-trigger">
            <a-avatar :src="loginUser.userAvatar" size="small" />
            <span class="username">{{ loginUser.userName || loginUser.userAccount }}</span>
          </div>
          <template #overlay>
            <div class="user-dropdown-content">
              <!-- 预算预警 -->
              <div v-if="statistics.dailyBudgetAlert || statistics.monthlyBudgetAlert" class="budget-alert">
                <WarningOutlined style="color: #faad14" />
                <span v-if="statistics.dailyBudgetAlert">今日预算已使用 {{ statistics.dailyBudgetUsagePercent?.toFixed(0) }}%</span>
                <span v-else-if="statistics.monthlyBudgetAlert">本月预算已使用 {{ statistics.monthlyBudgetUsagePercent?.toFixed(0) }}%</span>
              </div>
              <!-- 统计信息 -->
              <div class="statistics-section">
                <div class="stat-item">
                  <div class="stat-label">模型总数</div>
                  <div class="stat-value">{{ statistics.totalModels || 0 }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">总Tokens使用量</div>
                  <div class="stat-value">{{ formatNumber(statistics.totalTokens || 0) }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">总花费 (USD)</div>
                  <div class="stat-value">${{ formatCost(statistics.totalCost || 0) }}</div>
                </div>
                <div class="stat-item" v-if="statistics.dailyBudget">
                  <div class="stat-label">今日消耗</div>
                  <div class="stat-value" :style="{ color: getDailyBudgetColor() }">
                    ${{ formatCost(statistics.todayCost || 0) }} / ${{ formatCost(statistics.dailyBudget) }}
                  </div>
                </div>
                <div class="stat-item" v-if="statistics.monthlyBudget">
                  <div class="stat-label">本月消耗</div>
                  <div class="stat-value" :style="{ color: getMonthlyBudgetColor() }">
                    ${{ formatCost(statistics.monthCost || 0) }} / ${{ formatCost(statistics.monthlyBudget) }}
                  </div>
                </div>
              </div>
              <a-divider style="margin: 8px 0" />
              <a-menu>
                <a-menu-item @click="openEditModal">
                  <EditOutlined />
                  编辑信息
                </a-menu-item>
                <a-menu-item @click="handleLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </div>
          </template>
        </a-dropdown>

        <!-- 未登录：显示Login按钮 -->
        <button v-else class="login-btn-bottom" @click="openLoginModal">
          Login
        </button>
      </div>
    </aside>

    <!-- 主内容 -->
    <div class="main-wrapper">
      <RouterView />
    </div>

    <!-- 登录弹窗 -->
    <LoginModal />

    <!-- 用户编辑弹窗 -->
    <UserEditModal v-model:open="editModalVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { EditOutlined, LogoutOutlined, SwapOutlined, ExperimentOutlined, ThunderboltOutlined, UnorderedListOutlined, AppstoreOutlined, TrophyOutlined, DeleteOutlined, FileTextOutlined, BarChartOutlined, WarningOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { useLoginModalStore } from '@/stores/loginModal'
import { userLogout, getUserStatistics } from '@/api/userController'
import { listConversations, deleteConversation } from '@/api/conversationController'
import LoginModal from '@/components/LoginModal.vue'
import UserEditModal from '@/components/UserEditModal.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginModalStore = useLoginModalStore()
const loginUser = computed(() => loginUserStore.loginUser)

const openLoginModal = () => {
  loginModalStore.openModal('login')
}

// 用户编辑弹窗
const editModalVisible = ref(false)
const openEditModal = () => {
  editModalVisible.value = true
}

const todayConversations = ref<any[]>([])
const yesterdayConversations = ref<any[]>([])
const olderConversations = ref<any[]>([])

const currentPage = ref(1)
const pageSize = 50
const hasMore = ref(true)
const loading = ref(false)
const historyAreaRef = ref<HTMLElement | null>(null)

const statistics = ref({
  totalModels: 0,
  totalTokens: 0,
  totalCost: 0
})

const loadConversations = async (append: boolean = false) => {
  if (!loginUser.value.id || loading.value || (!append && !hasMore.value)) return
  
  try {
    loading.value = true
    
    const res: any = await listConversations({ pageNum: currentPage.value, pageSize })
    if (res.data && res.data.code === 0 && res.data.data && res.data.data.records) {
      const conversations = res.data.data.records
      const totalPages = res.data.data.totalPage || 1
      
      // 判断是否还有更多数据
      hasMore.value = currentPage.value < totalPages
      
      // 按时间分组
      const now = new Date()
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000)
      
      const todayList = conversations.filter((c: any) => {
        const createTime = new Date(c.createTime)
        return createTime >= today
      })
      
      const yesterdayList = conversations.filter((c: any) => {
        const createTime = new Date(c.createTime)
        return createTime >= yesterday && createTime < today
      })
      
      const olderList = conversations.filter((c: any) => {
        const createTime = new Date(c.createTime)
        return createTime < yesterday
      })
      
      if (append) {
        todayConversations.value = [...todayConversations.value, ...todayList]
        yesterdayConversations.value = [...yesterdayConversations.value, ...yesterdayList]
        olderConversations.value = [...olderConversations.value, ...olderList]
      } else {
        todayConversations.value = todayList
        yesterdayConversations.value = yesterdayList
        olderConversations.value = olderList
      }
    }
  } catch (error) {
    console.error('加载对话列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement
  const scrollTop = target.scrollTop
  const scrollHeight = target.scrollHeight
  const clientHeight = target.clientHeight
  
  if (scrollHeight - scrollTop - clientHeight < 100 && hasMore.value && !loading.value) {
    currentPage.value++
    loadConversations(true)
  }
}

const handleLogout = async () => {
  try {
    const res: any = await userLogout()
    if (res.data.code === 0) {
      loginUserStore.setLoginUser({ userName: '未登录' })
      message.success('退出成功')
      // 清空对话列表
      todayConversations.value = []
      yesterdayConversations.value = []
      olderConversations.value = []
      // 跳转到首页并刷新
      router.push('/side-by-side?t=' + Date.now())
    }
  } catch (error) {
    message.error('退出失败')
  }
}

const loadStatistics = async () => {
  if (!loginUser.value.id) return
  try {
    const res: any = await getUserStatistics()
    if (res.data?.code === 0 && res.data.data) {
      statistics.value = res.data.data
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const handleDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    loadStatistics()
  }
}

const formatNumber = (num: number) => {
  return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

const formatCost = (cost: number) => {
  return cost.toFixed(4)
}

const getDailyBudgetColor = () => {
  if (!statistics.value.dailyBudget) return '#1890ff'
  const percent = statistics.value.dailyBudgetUsagePercent || 0
  if (percent >= 100) return '#ff4d4f'
  if (percent >= 80) return '#faad14'
  return '#52c41a'
}

const getMonthlyBudgetColor = () => {
  if (!statistics.value.monthlyBudget) return '#1890ff'
  const percent = statistics.value.monthlyBudgetUsagePercent || 0
  if (percent >= 100) return '#ff4d4f'
  if (percent >= 80) return '#faad14'
  return '#52c41a'
}

const handleNewChat = () => {
  // 跳转到新对话页面，不带conversationId参数
  // 使用时间戳强制刷新
  router.push(`/side-by-side?t=${Date.now()}`)
}

const openConversation = (id: string, conversationType?: string, codePreviewEnabled?: boolean) => {
  // 根据会话类型和代码预览标识跳转到对应页面
  let page = 'side-by-side'
  
  if (codePreviewEnabled) {
    page = 'code-mode'
  } else if (conversationType === 'prompt_lab') {
    page = 'prompt-lab'
  } else if (conversationType === 'battle') {
    page = 'battle'
  }
  
  router.push(`/${page}?conversationId=${id}`)
}

const handleDeleteConversation = async (conversationId: string, event?: Event) => {
  if (event) {
    event.stopPropagation()
  }
  
  try {
    const res: any = await deleteConversation({ id: conversationId })
    if (res.data && res.data.code === 0) {
      message.success('会话已删除')
      
      todayConversations.value = todayConversations.value.filter((c: any) => c.id !== conversationId)
      yesterdayConversations.value = yesterdayConversations.value.filter((c: any) => c.id !== conversationId)
      olderConversations.value = olderConversations.value.filter((c: any) => c.id !== conversationId)
      
      const currentPath = router.currentRoute.value.fullPath
      if (currentPath.includes(`conversationId=${conversationId}`)) {
        const basePath = router.currentRoute.value.path
        router.replace(basePath)
      }
    } else {
      message.error(res.data?.message || '删除失败')
    }
  } catch (error) {
    console.error('删除会话失败:', error)
    message.error('删除会话失败')
  }
}

// 获取图标映射函数
const getIconFile = (modelId: string) => {
  const provider = modelId.split('/')[0]?.toLowerCase() || ''
  const iconMap: Record<string, string> = {
    'openai': 'openai.png',
    'anthropic': 'anthropic.png',
    'google': 'google.png',
    'meta-llama': 'meta-llama.png',
    'qwen': 'qwen.png',
    'alibaba': 'alibaba.png',
    'deepseek': 'deepseek.png',
    'baidu': 'baidu.png',
    'zhipu': 'zhipu.png',
    'z-ai': 'zhipu.png',
    'moonshot': 'moonshot.png',
    'moonshotai': 'moonshot.png',
    'tencent': 'tencent.png',
    'bytedance': 'bytedance.png',
    'bytedance-seed': 'bytedance.png',
    'meituan': 'meituan.png',
  }
  return iconMap[provider] || 'default.png'
}

// 获取所有模型的图标和名称（用于Popover显示）
const getAllModelLogos = (modelsJson: string) => {
  try {
    const models = JSON.parse(modelsJson || '[]')
    return models.map((modelId: string) => {
      const iconFile = getIconFile(modelId)
      return {
        url: new URL(`../assets/provider-icons/${iconFile}`, import.meta.url).href,
        alt: modelId.split('/')[0] || 'AI',
        name: modelId.split('/').pop() || modelId
      }
    })
  } catch {
    return []
  }
}

// 获取模型Logo URL（使用本地图标，最多显示2个）
const getModelLogos = (modelsJson: string) => {
  try {
    const models = JSON.parse(modelsJson || '[]')
    const totalCount = models.length
    
    // 最多显示2个图标
    const displayModels = models.slice(0, 2)
    const logos = displayModels.map((modelId: string) => {
      const iconFile = getIconFile(modelId)
      return {
        url: new URL(`../assets/provider-icons/${iconFile}`, import.meta.url).href,
        alt: modelId.split('/')[0] || 'AI',
        isMore: false
      }
    })
    
    // 如果超过2个，添加省略号图标
    if (totalCount > 2) {
      logos.push({
        url: '', // 不使用图片，用CSS显示省略号
        alt: `+${totalCount - 2}`,
        isMore: true
      })
    }
    
    return logos
  } catch {
    return [{ 
      url: new URL('../assets/provider-icons/default.png', import.meta.url).href, 
      alt: 'AI',
      isMore: false
    }]
  }
}

onMounted(() => {
  loadConversations()
  
  // 监听路由变化，刷新对话列表
  router.afterEach(() => {
    loadConversations()
  })
})

// 监听登录状态变化，登录后刷新对话列表
watch(
  () => loginUser.value.id,
  (newId, oldId) => {
    if (newId && !oldId) {
      // 用户刚登录，刷新对话列表
      currentPage.value = 1
      hasMore.value = true
      loadConversations()
    } else if (!newId && oldId) {
      // 用户退出登录，清空对话列表并跳转到首页
      todayConversations.value = []
      yesterdayConversations.value = []
      olderConversations.value = []
      router.push('/side-by-side')
    }
  }
)
</script>

<style scoped>
.arena-container {
  display: flex;
  height: 100vh;
  background: #fff;
}

/* 左侧边栏 */
.left-sidebar {
  width: 208px;
  background: #f7f7f8;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.logo-section {
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #e5e7eb;
}

.logo-img {
  width: 28px;
  height: 28px;
}

.app-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.nav-buttons {
  padding: 12px;
  border-bottom: 1px solid #e5e7eb;
}

.nav-btn {
  width: 100%;
  padding: 10px 14px;
  background: transparent;
  border: none;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-size: 14px;
  color: #374151;
  transition: background 0.15s;
  margin-bottom: 4px;
  text-align: left;
}

.nav-btn:hover {
  background: #e5e7eb;
}

.history-area {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.history-area::-webkit-scrollbar {
  width: 6px;
}

.history-area::-webkit-scrollbar-track {
  background: transparent;
}

.history-area::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.history-area::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}

.loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.no-more {
  text-align: center;
  padding: 16px;
}

.history-group {
  margin-bottom: 24px;
}

.group-label {
  font-size: 11px;
  color: #6b7280;
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.history-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #374151;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s;
  position: relative;
}

.history-item:hover {
  background: #e5e7eb;
}

.history-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s, background 0.15s;
  flex-shrink: 0;
}

.delete-btn:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.conversation-type-icon {
  width: 36px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}

.model-logos {
  width: 36px;
  display: flex;
  gap: 2px;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}

.model-logo {
  width: 14px;
  height: 14px;
  border-radius: 2px;
  object-fit: contain;
}

.more-models {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 14px;
  height: 14px;
  font-size: 9px;
  color: #6b7280;
  background: #e5e7eb;
  border-radius: 2px;
  padding: 0 3px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.more-models:hover {
  background: #d1d5db;
  color: #374151;
}

.history-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
  font-size: 13px;
}

/* Popover样式 */
.all-models-popup {
  max-width: 250px;
  padding: 4px 0;
}

.popup-model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.popup-model-item:hover {
  background: #f3f4f6;
}

.popup-model-logo {
  width: 20px;
  height: 20px;
  border-radius: 3px;
  object-fit: contain;
  flex-shrink: 0;
}

.popup-model-name {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
}

.user-area {
  padding: 12px;
  border-top: 1px solid #e5e7eb;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-trigger:hover {
  background: #e5e7eb;
}

.username {
  font-size: 13px;
  color: #374151;
}

.login-btn-bottom {
  width: 100%;
  padding: 10px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s;
}

.login-btn-bottom:hover {
  background: #333;
}

.main-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
}

.user-dropdown-content {
  min-width: 240px;
  background: #fff;
}

.budget-alert {
  padding: 8px 16px;
  background: #fffbe6;
  border-bottom: 1px solid #ffe58f;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #d48806;
}

.statistics-section {
  padding: 16px;
  background: #fafafa;
}

.stat-item {
  margin-bottom: 12px;
}

.stat-item:last-child {
  margin-bottom: 0;
}

.stat-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #1890ff;
}
</style>
