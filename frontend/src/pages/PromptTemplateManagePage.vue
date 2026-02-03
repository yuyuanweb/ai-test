<template>
  <div class="prompt-template-page">
    <header class="page-header">
      <h1 class="page-title">
        <FileTextOutlined style="margin-right: 8px" />
        提示词模板库
      </h1>
      <div class="header-actions">
        <a-input-search
          v-model:value="searchText"
          placeholder="搜索模板名称..."
          style="width: 200px"
          allow-clear
          @search="handleSearch"
        />
        <a-select
          v-model:value="filterStrategy"
          placeholder="筛选策略"
          style="width: 140px"
          allow-clear
          @change="handleFilter"
        >
          <a-select-option value="direct">直接提问</a-select-option>
          <a-select-option value="cot">思维链</a-select-option>
          <a-select-option value="role_play">角色扮演</a-select-option>
          <a-select-option value="few_shot">少样本</a-select-option>
        </a-select>
        <a-button type="primary" @click="showCreateModal">
          <PlusOutlined />
          创建模板
        </a-button>
      </div>
    </header>

    <div class="content-area">
      <a-spin :spinning="loading">
        <!-- 预设模板区域 -->
        <div class="template-section">
          <h2 class="section-title">
            <LockOutlined style="margin-right: 8px" />
            预设模板
            <span class="section-desc">（系统提供的模板，仅可查看和使用）</span>
          </h2>
          <div class="template-grid">
            <div
              v-for="template in filteredPresetTemplates"
              :key="template.id"
              class="template-card preset"
              @click="viewTemplate(template)"
            >
              <div class="card-header">
                <span class="template-name">{{ template.name }}</span>
                <a-tag :color="getStrategyColor(template.strategy)">
                  {{ template.strategyName || getStrategyName(template.strategy) }}
                </a-tag>
              </div>
              <p class="template-desc">{{ template.description || '暂无描述' }}</p>
              <div class="card-footer">
                <span class="usage-count">
                  <FireOutlined />
                  使用 {{ template.usageCount || 0 }} 次
                </span>
                <a-button type="link" size="small" @click.stop="useTemplate(template)">
                  使用
                </a-button>
              </div>
            </div>
            <div v-if="filteredPresetTemplates.length === 0" class="empty-tip">
              暂无预设模板
            </div>
          </div>
        </div>

        <!-- 我的模板区域 -->
        <div class="template-section">
          <h2 class="section-title">
            <UserOutlined style="margin-right: 8px" />
            我的模板
            <span class="section-desc">（您创建的模板，可以编辑和删除）</span>
          </h2>
          <div class="template-grid">
            <div
              v-for="template in filteredMyTemplates"
              :key="template.id"
              class="template-card my-template"
              @click="viewTemplate(template)"
            >
              <div class="card-header">
                <span class="template-name">{{ template.name }}</span>
                <a-tag :color="getStrategyColor(template.strategy)">
                  {{ template.strategyName || getStrategyName(template.strategy) }}
                </a-tag>
              </div>
              <p class="template-desc">{{ template.description || '暂无描述' }}</p>
              <div class="card-footer">
                <span class="usage-count">
                  <FireOutlined />
                  使用 {{ template.usageCount || 0 }} 次
                </span>
                <div class="card-actions">
                  <a-button type="link" size="small" @click.stop="useTemplate(template)">
                    使用
                  </a-button>
                  <a-button type="link" size="small" @click.stop="editTemplate(template)">
                    编辑
                  </a-button>
                  <a-popconfirm
                    title="确定要删除这个模板吗？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="handleDelete(template.id)"
                  >
                    <a-button type="link" size="small" danger @click.stop>
                      删除
                    </a-button>
                  </a-popconfirm>
                </div>
              </div>
            </div>
            <div v-if="filteredMyTemplates.length === 0" class="empty-tip">
              您还没有创建任何模板，点击右上角"创建模板"开始
            </div>
          </div>
        </div>
      </a-spin>
    </div>

    <!-- 创建/编辑模板弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEditing ? '编辑模板' : '创建模板'"
      :width="720"
      :mask-closable="false"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :rules="formRules"
        layout="vertical"
      >
        <a-form-item label="模板名称" name="name">
          <a-input v-model:value="formState.name" placeholder="请输入模板名称" />
        </a-form-item>
        <a-form-item label="策略类型" name="strategy">
          <a-select v-model:value="formState.strategy" placeholder="请选择策略类型">
            <a-select-option value="direct">直接提问</a-select-option>
            <a-select-option value="cot">思维链 (Chain of Thought)</a-select-option>
            <a-select-option value="role_play">角色扮演</a-select-option>
            <a-select-option value="few_shot">少样本 (Few-shot)</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分类" name="category">
          <a-input v-model:value="formState.category" placeholder="请输入分类，如：通用、编程、写作" />
        </a-form-item>
        <a-form-item label="模板描述" name="description">
          <a-textarea
            v-model:value="formState.description"
            placeholder="请输入模板描述"
            :rows="2"
          />
        </a-form-item>
        <a-form-item label="模板内容" name="content">
          <a-textarea
            v-model:value="formState.content"
            placeholder="请输入模板内容，可使用 {variable} 格式的占位符"
            :rows="6"
          />
          <div class="form-tip">
            提示：使用 {变量名} 格式定义占位符，如 {question}、{role}
          </div>
        </a-form-item>
        <a-form-item label="变量列表" name="variables">
          <a-select
            v-model:value="formState.variables"
            mode="tags"
            placeholder="输入变量名后按回车添加"
          />
          <div class="form-tip">
            从模板内容中提取的变量会自动添加，您也可以手动添加
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 查看模板弹窗 -->
    <a-modal
      v-model:open="viewModalVisible"
      title="查看模板"
      :width="640"
      :footer="null"
    >
      <div v-if="viewingTemplate" class="template-detail">
        <div class="detail-row">
          <span class="detail-label">模板名称：</span>
          <span class="detail-value">{{ viewingTemplate.name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">策略类型：</span>
          <a-tag :color="getStrategyColor(viewingTemplate.strategy)">
            {{ viewingTemplate.strategyName || getStrategyName(viewingTemplate.strategy) }}
          </a-tag>
        </div>
        <div class="detail-row">
          <span class="detail-label">分类：</span>
          <span class="detail-value">{{ viewingTemplate.category || '未分类' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">描述：</span>
          <span class="detail-value">{{ viewingTemplate.description || '暂无描述' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">模板内容：</span>
        </div>
        <pre class="template-content-preview">{{ viewingTemplate.content }}</pre>
        <div class="detail-row">
          <span class="detail-label">变量列表：</span>
          <span class="detail-value">
            <a-tag v-for="v in viewingTemplate.variables" :key="v" color="blue">
              {{ v }}
            </a-tag>
            <span v-if="!viewingTemplate.variables?.length">无</span>
          </span>
        </div>
        <div class="detail-actions">
          <a-button type="primary" @click="useTemplate(viewingTemplate)">
            使用此模板
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  FileTextOutlined,
  PlusOutlined,
  LockOutlined,
  UserOutlined,
  FireOutlined,
} from '@ant-design/icons-vue'
import {
  listTemplates,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  incrementUsage,
  type PromptTemplateVO,
  type CreatePromptTemplateRequest,
  type UpdatePromptTemplateRequest,
} from '@/api/promptTemplateController'

const router = useRouter()

const loading = ref(false)
const templates = ref<PromptTemplateVO[]>([])
const searchText = ref('')
const filterStrategy = ref<string | undefined>(undefined)

const presetTemplates = computed(() =>
  templates.value.filter((t) => t.isPreset)
)
const myTemplates = computed(() =>
  templates.value.filter((t) => !t.isPreset)
)

const filteredPresetTemplates = computed(() => {
  let result = presetTemplates.value
  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    result = result.filter(
      (t) =>
        t.name?.toLowerCase().includes(search) ||
        t.description?.toLowerCase().includes(search)
    )
  }
  if (filterStrategy.value) {
    result = result.filter((t) => t.strategy === filterStrategy.value)
  }
  return result
})

const filteredMyTemplates = computed(() => {
  let result = myTemplates.value
  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    result = result.filter(
      (t) =>
        t.name?.toLowerCase().includes(search) ||
        t.description?.toLowerCase().includes(search)
    )
  }
  if (filterStrategy.value) {
    result = result.filter((t) => t.strategy === filterStrategy.value)
  }
  return result
})

const modalVisible = ref(false)
const isEditing = ref(false)
const editingTemplateId = ref<string | null>(null)
const formRef = ref()
const formState = ref<{
  name: string
  strategy: string
  category: string
  description: string
  content: string
  variables: string[]
}>({
  name: '',
  strategy: 'direct',
  category: '',
  description: '',
  content: '',
  variables: [],
})

const formRules = {
  name: [{ required: true, message: '请输入模板名称' }],
  strategy: [{ required: true, message: '请选择策略类型' }],
  content: [{ required: true, message: '请输入模板内容' }],
}

const viewModalVisible = ref(false)
const viewingTemplate = ref<PromptTemplateVO | null>(null)

const getStrategyName = (strategy?: string) => {
  const map: Record<string, string> = {
    direct: '直接提问',
    cot: '思维链',
    role_play: '角色扮演',
    few_shot: '少样本',
  }
  return map[strategy || ''] || strategy
}

const getStrategyColor = (strategy?: string) => {
  const map: Record<string, string> = {
    direct: 'blue',
    cot: 'purple',
    role_play: 'green',
    few_shot: 'orange',
  }
  return map[strategy || ''] || 'default'
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const res: any = await listTemplates()
    console.log('📦 模板列表响应:', res)
    if (res.data && res.data.code === 0) {
      templates.value = res.data.data || []
      console.log('📋 全部模板:', templates.value.length, '个')
      console.log('📋 预设模板:', presetTemplates.value.length, '个')
      console.log('📋 我的模板:', myTemplates.value.length, '个')
      console.log('📋 模板详情:', templates.value.map(t => ({ name: t.name, isPreset: t.isPreset })))
    } else {
      message.error(res.data?.message || '加载模板失败')
    }
  } catch (error) {
    console.error('加载模板失败:', error)
    message.error('加载模板失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 搜索是响应式的，不需要额外操作
}

const handleFilter = () => {
  // 筛选是响应式的，不需要额外操作
}

const showCreateModal = () => {
  isEditing.value = false
  editingTemplateId.value = null
  formState.value = {
    name: '',
    strategy: 'direct',
    category: '',
    description: '',
    content: '',
    variables: [],
  }
  modalVisible.value = true
}

const editTemplate = (template: PromptTemplateVO) => {
  isEditing.value = true
  editingTemplateId.value = template.id || null
  formState.value = {
    name: template.name || '',
    strategy: template.strategy || 'direct',
    category: template.category || '',
    description: template.description || '',
    content: template.content || '',
    variables: template.variables || [],
  }
  modalVisible.value = true
}

const viewTemplate = (template: PromptTemplateVO) => {
  viewingTemplate.value = template
  viewModalVisible.value = true
}

const useTemplate = async (template: PromptTemplateVO) => {
  if (template.id) {
    try {
      await incrementUsage(template.id)
    } catch (e) {
      console.warn('增加使用次数失败', e)
    }
  }
  router.push({
    path: '/prompt-lab',
    query: { templateId: template.id },
  })
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    
    if (isEditing.value && editingTemplateId.value) {
      const data: UpdatePromptTemplateRequest = {
        id: editingTemplateId.value,
        name: formState.value.name,
        strategy: formState.value.strategy,
        category: formState.value.category,
        description: formState.value.description,
        content: formState.value.content,
        variables: formState.value.variables,
      }
      const res: any = await updateTemplate(data)
      if (res.data && res.data.code === 0) {
        message.success('更新成功')
        modalVisible.value = false
        loadTemplates()
      } else {
        message.error(res.data?.message || '更新失败')
      }
    } else {
      const data: CreatePromptTemplateRequest = {
        name: formState.value.name,
        strategy: formState.value.strategy,
        category: formState.value.category,
        description: formState.value.description,
        content: formState.value.content,
        variables: formState.value.variables,
      }
      const res: any = await createTemplate(data)
      if (res.data && res.data.code === 0) {
        message.success('创建成功')
        modalVisible.value = false
        loadTemplates()
      } else {
        message.error(res.data?.message || '创建失败')
      }
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

const handleCancel = () => {
  modalVisible.value = false
}

const handleDelete = async (templateId?: string) => {
  if (!templateId) return
  try {
    const res: any = await deleteTemplate(templateId)
    if (res.data && res.data.code === 0) {
      message.success('删除成功')
      loadTemplates()
    } else {
      message.error(res.data?.message || '删除失败')
    }
  } catch (error) {
    console.error('删除失败:', error)
    message.error('删除失败')
  }
}

watch(
  () => formState.value.content,
  (newContent) => {
    if (newContent) {
      const matches = newContent.match(/\{(\w+)\}/g)
      if (matches) {
        const vars = matches.map((m) => m.slice(1, -1))
        const uniqueVars = [...new Set(vars)]
        formState.value.variables = uniqueVars
      }
    }
  }
)

onMounted(() => {
  loadTemplates()
})
</script>

<style scoped>
.prompt-template-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  display: flex;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.content-area {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.template-section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}

.section-desc {
  font-size: 12px;
  color: #999;
  font-weight: normal;
  margin-left: 8px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.template-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e8e8e8;
}

.template-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.template-card.preset {
  border-left: 3px solid #1890ff;
}

.template-card.my-template {
  border-left: 3px solid #52c41a;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.template-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.template-desc {
  font-size: 13px;
  color: #666;
  margin: 0 0 12px 0;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.usage-count {
  font-size: 12px;
  color: #999;
}

.card-actions {
  display: flex;
  gap: 4px;
}

.empty-tip {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 20px;
  color: #999;
  font-size: 14px;
}

.template-detail {
  padding: 8px 0;
}

.detail-row {
  margin-bottom: 12px;
}

.detail-label {
  font-weight: 500;
  color: #666;
  margin-right: 8px;
}

.detail-value {
  color: #333;
}

.template-content-preview {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 8px 0 16px 0;
}

.detail-actions {
  margin-top: 20px;
  text-align: right;
}

.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
