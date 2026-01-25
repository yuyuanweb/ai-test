<template>
  <div class="scene-manage-page">
    <a-card title="场景管理" :bordered="false">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="handleCreateScene">
            <template #icon><PlusOutlined /></template>
            创建场景
          </a-button>
        </a-space>
      </template>

      <!-- 筛选区域 -->
      <div class="filter-section">
        <a-form layout="inline" :model="filterForm" class="filter-form">
          <a-form-item label="分类">
            <a-select
              v-model:value="filterForm.category"
              placeholder="选择分类"
              allow-clear
              style="width: 150px"
            >
              <a-select-option value="">全部</a-select-option>
              <a-select-option v-for="cat in categories" :key="cat" :value="cat">
                {{ cat }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="类型">
            <a-select
              v-model:value="filterForm.isPreset"
              placeholder="选择类型"
              allow-clear
              style="width: 150px"
            >
              <a-select-option :value="undefined">全部</a-select-option>
              <a-select-option :value="true">预设场景</a-select-option>
              <a-select-option :value="false">自定义场景</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button style="margin-left: 8px" @click="handleReset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
          </a-form-item>
        </a-form>
      </div>

      <!-- 场景列表 -->
      <a-table
        :columns="columns"
        :data-source="scenes"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'isPreset'">
            <a-tag :color="record.isPreset ? 'blue' : 'green'">
              {{ record.isPreset ? '预设' : '自定义' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'category'">
            <a-tag v-if="record.category">{{ record.category }}</a-tag>
            <span v-else style="color: #999">未分类</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleManagePrompts(record)">
                管理提示词
              </a-button>
              <a-button
                v-if="!record.isPreset"
                type="link"
                size="small"
                @click="handleEditScene(record)"
              >
                编辑
              </a-button>
              <a-popconfirm
                v-if="!record.isPreset"
                title="确定要删除这个场景吗？"
                @confirm="handleDeleteScene(record.id)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑场景模态框 -->
    <a-modal
      v-model:open="sceneModalVisible"
      :title="editingScene ? '编辑场景' : '创建场景'"
      @ok="handleSaveScene"
      @cancel="handleCancelScene"
    >
      <a-form :model="sceneForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="场景名称" :rules="[{ required: true, message: '请输入场景名称' }]">
          <a-input v-model:value="sceneForm.name" placeholder="请输入场景名称" />
        </a-form-item>
        <a-form-item label="场景描述">
          <a-textarea
            v-model:value="sceneForm.description"
            placeholder="请输入场景描述（可选）"
            :rows="3"
          />
        </a-form-item>
        <a-form-item label="分类">
          <a-select
            v-model:value="sceneForm.category"
            placeholder="选择分类（可选）"
            allow-clear
            style="width: 100%"
          >
            <a-select-option v-for="cat in categories" :key="cat" :value="cat">
              {{ cat }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 提示词管理模态框 -->
    <a-modal
      v-model:open="promptModalVisible"
      :title="currentScene ? `管理提示词 - ${currentScene.name}` : '管理提示词'"
      width="90%"
      :footer="null"
      @cancel="handleClosePromptModal"
    >
      <div v-if="currentScene" class="prompt-management">
        <div class="prompt-actions">
          <a-space>
            <a-button
              v-if="!currentScene?.isPreset"
              type="primary"
              @click="handleAddPrompt"
            >
              <template #icon><PlusOutlined /></template>
              添加提示词
            </a-button>
            <a-upload
              v-if="!currentScene?.isPreset"
              :before-upload="handleBeforeUpload"
              :show-upload-list="false"
              accept=".csv,.json"
            >
              <a-button>
                <template #icon><UploadOutlined /></template>
                批量导入
              </a-button>
            </a-upload>
            <a-button @click="handleExportPrompts">
              <template #icon><DownloadOutlined /></template>
              导出提示词
            </a-button>
          </a-space>
        </div>

        <a-table
          :columns="promptColumns"
          :data-source="prompts"
          :loading="loadingPrompts"
          row-key="id"
          style="margin-top: 16px"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'content'">
              <a-typography-paragraph
                :ellipsis="{ rows: 2, expandable: true }"
                style="max-width: 400px"
              >
                {{ record.content }}
              </a-typography-paragraph>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space>
                <a-button
                  v-if="!currentScene?.isPreset"
                  type="link"
                  size="small"
                  @click="handleEditPrompt(record)"
                >
                  编辑
                </a-button>
                <a-popconfirm
                  v-if="!currentScene?.isPreset"
                  title="确定要删除这个提示词吗？"
                  @confirm="handleDeletePrompt(record.id)"
                >
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
                <span v-if="currentScene?.isPreset" style="color: #999; font-size: 12px">
                  内置场景不可编辑
                </span>
              </a-space>
            </template>
          </template>
        </a-table>
      </div>
    </a-modal>

    <!-- 添加/编辑提示词模态框 -->
    <a-modal
      v-model:open="promptFormModalVisible"
      :title="editingPrompt ? '编辑提示词' : '添加提示词'"
      @ok="handleSavePrompt"
      @cancel="handleCancelPrompt"
      width="800px"
    >
      <a-form :model="promptForm" :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
        <a-form-item label="标题" :rules="[{ required: true, message: '请输入提示词标题' }]">
          <a-input v-model:value="promptForm.title" placeholder="请输入提示词标题" />
        </a-form-item>
        <a-form-item label="内容" :rules="[{ required: true, message: '请输入提示词内容' }]">
          <a-textarea
            v-model:value="promptForm.content"
            placeholder="请输入提示词内容"
            :rows="6"
          />
        </a-form-item>
        <a-form-item label="难度">
          <a-select v-model:value="promptForm.difficulty" placeholder="选择难度（可选）" allow-clear>
            <a-select-option value="easy">简单</a-select-option>
            <a-select-option value="medium">中等</a-select-option>
            <a-select-option value="hard">困难</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="期望输出">
          <a-textarea
            v-model:value="promptForm.expectedOutput"
            placeholder="请输入期望输出（可选）"
            :rows="3"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  UploadOutlined,
  DownloadOutlined
} from '@ant-design/icons-vue'
import {
  listScenes,
  createScene,
  updateScene,
  deleteScene,
  getScenePrompts,
  addScenePrompt,
  updateScenePrompt,
  deleteScenePrompt,
  type Scene,
  type ScenePrompt,
  type CreateSceneRequest,
  type UpdateSceneRequest,
  type AddScenePromptRequest,
  type UpdateScenePromptRequest
} from '@/api/sceneController'
import type { UploadProps } from 'ant-design-vue'

const scenes = ref<Scene[]>([])
const loading = ref(false)
const categories = ref<string[]>([])
const filterForm = reactive({
  category: '',
  isPreset: undefined as boolean | undefined
})

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
})

const columns = [
  {
    title: '场景名称',
    dataIndex: 'name',
    key: 'name'
  },
  {
    title: '分类',
    key: 'category',
    width: 120
  },
  {
    title: '类型',
    key: 'isPreset',
    width: 100
  },
  {
    title: '描述',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180
  },
  {
    title: '操作',
    key: 'action',
    width: 250,
    fixed: 'right'
  }
]

const sceneModalVisible = ref(false)
const editingScene = ref<Scene | null>(null)
const sceneForm = reactive<CreateSceneRequest>({
  name: '',
  description: '',
  category: ''
})

const promptModalVisible = ref(false)
const currentScene = ref<Scene | null>(null)
const prompts = ref<ScenePrompt[]>([])
const loadingPrompts = ref(false)

const promptFormModalVisible = ref(false)
const editingPrompt = ref<ScenePrompt | null>(null)
const promptForm = reactive<AddScenePromptRequest>({
  sceneId: '',
  title: '',
  content: '',
  difficulty: '',
  expectedOutput: ''
})

const promptColumns = [
  {
    title: '序号',
    dataIndex: 'promptIndex',
    key: 'promptIndex',
    width: 80
  },
  {
    title: '标题',
    dataIndex: 'title',
    key: 'title',
    width: 200
  },
  {
    title: '内容',
    key: 'content',
    ellipsis: true
  },
  {
    title: '难度',
    dataIndex: 'difficulty',
    key: 'difficulty',
    width: 100
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    fixed: 'right'
  }
]

const loadCategories = async () => {
  try {
    const res = await listScenes({
      pageNum: 1,
      pageSize: 1000,
      isPreset: true
    })
    if (res.data?.code === 0 && res.data.data) {
      const categorySet = new Set<string>()
      res.data.data.records?.forEach((scene: Scene) => {
        if (scene.category) {
          categorySet.add(scene.category)
        }
      })
      categories.value = Array.from(categorySet).sort()
    }
  } catch (error) {
    console.error('加载分类列表失败:', error)
  }
}

const loadScenes = async () => {
  try {
    loading.value = true
    const params: any = {
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize
    }

    if (filterForm.category) {
      params.category = filterForm.category
    }

    if (filterForm.isPreset !== undefined) {
      params.isPreset = filterForm.isPreset
    }

    const res = await listScenes(params)

    if (res.data?.code === 0 && res.data.data) {
      scenes.value = res.data.data.records || []
      pagination.value.total = res.data.data.totalRow || res.data.data.total || 0
    }
  } catch (error) {
    console.error('加载场景列表失败:', error)
    message.error('加载场景列表失败')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pag: any) => {
  pagination.value.current = pag.current
  pagination.value.pageSize = pag.pageSize
  loadScenes()
}

const handleSearch = () => {
  pagination.value.current = 1
  loadScenes()
}

const handleReset = () => {
  filterForm.category = ''
  filterForm.isPreset = undefined
  pagination.value.current = 1
  loadScenes()
}

const handleCreateScene = () => {
  editingScene.value = null
  sceneForm.name = ''
  sceneForm.description = ''
  sceneForm.category = ''
  sceneModalVisible.value = true
}

const handleEditScene = (scene: Scene) => {
  editingScene.value = scene
  sceneForm.name = scene.name || ''
  sceneForm.description = scene.description || ''
  sceneForm.category = scene.category || ''
  sceneModalVisible.value = true
}

const handleSaveScene = async () => {
  if (!sceneForm.name || !sceneForm.name.trim()) {
    message.error('请输入场景名称')
    return
  }

  try {
    if (editingScene.value) {
      const updateRequest: UpdateSceneRequest = {
        id: editingScene.value.id,
        name: sceneForm.name,
        description: sceneForm.description,
        category: sceneForm.category
      }
      const res = await updateScene(updateRequest)
      if (res.data?.code === 0) {
        message.success('更新成功')
        sceneModalVisible.value = false
        loadScenes()
      } else {
        message.error(res.data?.message || '更新失败')
      }
    } else {
      const res = await createScene(sceneForm)
      if (res.data?.code === 0) {
        message.success('创建成功')
        sceneModalVisible.value = false
        loadScenes()
        loadCategories()
      } else {
        message.error(res.data?.message || '创建失败')
      }
    }
  } catch (error: any) {
    console.error('保存场景失败:', error)
    message.error(error.message || '保存场景失败')
  }
}

const handleCancelScene = () => {
  sceneModalVisible.value = false
  editingScene.value = null
}

const handleDeleteScene = async (sceneId: string) => {
  try {
    const res = await deleteScene(sceneId)
    if (res.data?.code === 0) {
      message.success('删除成功')
      loadScenes()
    } else {
      message.error(res.data?.message || '删除失败')
    }
  } catch (error: any) {
    console.error('删除场景失败:', error)
    message.error(error.message || '删除场景失败')
  }
}

const handleManagePrompts = async (scene: Scene) => {
  currentScene.value = scene
  promptModalVisible.value = true
  await loadPrompts(scene.id)
}

const loadPrompts = async (sceneId: string) => {
  try {
    loadingPrompts.value = true
    const res = await getScenePrompts(sceneId)
    if (res.data?.code === 0 && res.data.data) {
      prompts.value = res.data.data || []
    }
  } catch (error) {
    console.error('加载提示词失败:', error)
    message.error('加载提示词失败')
  } finally {
    loadingPrompts.value = false
  }
}

const handleClosePromptModal = () => {
  promptModalVisible.value = false
  currentScene.value = null
  prompts.value = []
}

const handleAddPrompt = () => {
  if (!currentScene.value) {
    message.error('请先选择场景')
    return
  }
  editingPrompt.value = null
  promptForm.sceneId = currentScene.value.id
  promptForm.title = ''
  promptForm.content = ''
  promptForm.difficulty = ''
  promptForm.expectedOutput = ''
  promptFormModalVisible.value = true
}

const handleEditPrompt = (prompt: ScenePrompt) => {
  editingPrompt.value = prompt
  promptForm.sceneId = prompt.sceneId
  promptForm.title = prompt.title || ''
  promptForm.content = prompt.content || ''
  promptForm.difficulty = prompt.difficulty || ''
  promptForm.expectedOutput = prompt.expectedOutput || ''
  promptFormModalVisible.value = true
}

const handleSavePrompt = async () => {
  if (!promptForm.title || !promptForm.title.trim()) {
    message.error('请输入提示词标题')
    return
  }
  if (!promptForm.content || !promptForm.content.trim()) {
    message.error('请输入提示词内容')
    return
  }

  try {
    if (editingPrompt.value) {
      const updateRequest: UpdateScenePromptRequest = {
        id: editingPrompt.value.id,
        title: promptForm.title,
        content: promptForm.content,
        difficulty: promptForm.difficulty,
        expectedOutput: promptForm.expectedOutput
      }
      const res = await updateScenePrompt(updateRequest)
      if (res.data?.code === 0) {
        message.success('更新成功')
        promptFormModalVisible.value = false
        if (currentScene.value) {
          await loadPrompts(currentScene.value.id)
        }
      } else {
        message.error(res.data?.message || '更新失败')
      }
    } else {
      const res = await addScenePrompt(promptForm)
      if (res.data?.code === 0) {
        message.success('添加成功')
        promptFormModalVisible.value = false
        if (currentScene.value) {
          await loadPrompts(currentScene.value.id)
        }
      } else {
        message.error(res.data?.message || '添加失败')
      }
    }
  } catch (error: any) {
    console.error('保存提示词失败:', error)
    message.error(error.message || '保存提示词失败')
  }
}

const handleCancelPrompt = () => {
  promptFormModalVisible.value = false
  editingPrompt.value = null
}

const handleDeletePrompt = async (promptId: string) => {
  try {
    const res = await deleteScenePrompt(promptId)
    if (res.data?.code === 0) {
      message.success('删除成功')
      if (currentScene.value) {
        await loadPrompts(currentScene.value.id)
      }
    } else {
      message.error(res.data?.message || '删除失败')
    }
  } catch (error: any) {
    console.error('删除提示词失败:', error)
    message.error(error.message || '删除提示词失败')
  }
}

const handleBeforeUpload: UploadProps['beforeUpload'] = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const content = e.target?.result as string
      const fileName = file.name.toLowerCase()

      if (fileName.endsWith('.csv')) {
        parseCSV(content)
      } else if (fileName.endsWith('.json')) {
        parseJSON(content)
      } else {
        message.error('不支持的文件格式，请上传CSV或JSON文件')
      }
    } catch (error) {
      console.error('解析文件失败:', error)
      message.error('解析文件失败，请检查文件格式')
    }
  }
  reader.readAsText(file)
  return false
}

const parseCSVLine = (line: string): string[] => {
  const result: string[] = []
  let current = ''
  let inQuotes = false

  for (let i = 0; i < line.length; i++) {
    const char = line[i]
    if (char === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"'
        i++
      } else {
        inQuotes = !inQuotes
      }
    } else if (char === ',' && !inQuotes) {
      result.push(current.trim())
      current = ''
    } else {
      current += char
    }
  }
  result.push(current.trim())
  return result
}

const parseCSV = (content: string) => {
  const lines = content.split('\n').filter(line => line.trim())
  if (lines.length < 2) {
    message.error('CSV文件至少需要包含标题行和一行数据')
    return
  }

  const headers = parseCSVLine(lines[0])
  const titleIndex = headers.findIndex(h => h.toLowerCase().includes('title') || h.toLowerCase().includes('标题'))
  const contentIndex = headers.findIndex(h => h.toLowerCase().includes('content') || h.toLowerCase().includes('内容'))
  const difficultyIndex = headers.findIndex(h => h.toLowerCase().includes('difficulty') || h.toLowerCase().includes('难度'))
  const expectedOutputIndex = headers.findIndex(h => h.toLowerCase().includes('expected') || h.toLowerCase().includes('期望'))

  if (titleIndex === -1 || contentIndex === -1) {
    message.error('CSV文件必须包含"标题"和"内容"列')
    return
  }

  const promptsToAdd: AddScenePromptRequest[] = []
  for (let i = 1; i < lines.length; i++) {
    const values = parseCSVLine(lines[i])
    if (values[titleIndex] && values[contentIndex]) {
      promptsToAdd.push({
        sceneId: currentScene.value!.id,
        title: values[titleIndex].replace(/^"|"$/g, ''),
        content: values[contentIndex].replace(/^"|"$/g, ''),
        difficulty: difficultyIndex >= 0 ? values[difficultyIndex].replace(/^"|"$/g, '') : '',
        expectedOutput: expectedOutputIndex >= 0 ? values[expectedOutputIndex].replace(/^"|"$/g, '') : ''
      })
    }
  }

  if (promptsToAdd.length === 0) {
    message.error('CSV文件中没有有效数据')
    return
  }

  importPrompts(promptsToAdd)
}

const parseJSON = (content: string) => {
  try {
    const data = JSON.parse(content)
    const promptsArray = Array.isArray(data) ? data : [data]

    const promptsToAdd: AddScenePromptRequest[] = promptsArray.map((item: any) => ({
      sceneId: currentScene.value!.id,
      title: item.title || item.name || '',
      content: item.content || item.prompt || '',
      difficulty: item.difficulty || '',
      expectedOutput: item.expectedOutput || item.expected || ''
    }))

    if (promptsToAdd.length === 0) {
      message.error('JSON文件中没有有效数据')
      return
    }

    importPrompts(promptsToAdd)
  } catch (error) {
    console.error('解析JSON失败:', error)
    message.error('JSON格式错误，请检查文件内容')
  }
}

const importPrompts = async (promptsToAdd: AddScenePromptRequest[]) => {
  try {
    let successCount = 0
    let failCount = 0

    for (const prompt of promptsToAdd) {
      try {
        const res = await addScenePrompt(prompt)
        if (res.data?.code === 0) {
          successCount++
        } else {
          failCount++
        }
      } catch (error) {
        failCount++
      }
    }

    message.success(`导入完成：成功 ${successCount} 条，失败 ${failCount} 条`)
    if (currentScene.value) {
      await loadPrompts(currentScene.value.id)
    }
  } catch (error) {
    console.error('批量导入失败:', error)
    message.error('批量导入失败')
  }
}

const handleExportPrompts = () => {
  if (!currentScene.value || prompts.value.length === 0) {
    message.warning('没有可导出的提示词')
    return
  }

  const csvContent = [
    ['标题', '内容', '难度', '期望输出'].join(','),
    ...prompts.value.map(p => [
      `"${(p.title || '').replace(/"/g, '""')}"`,
      `"${(p.content || '').replace(/"/g, '""')}"`,
      `"${(p.difficulty || '')}"`,
      `"${(p.expectedOutput || '').replace(/"/g, '""')}"`
    ].join(','))
  ].join('\n')

  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  const url = URL.createObjectURL(blob)
  link.setAttribute('href', url)
  link.setAttribute('download', `${currentScene.value.name}_提示词.csv`)
  link.style.visibility = 'hidden'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(() => {
  loadCategories()
  loadScenes()
})
</script>

<style scoped>
.scene-manage-page {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
}

.filter-section {
  margin-bottom: 16px;
  padding: 16px;
  background-color: #fafafa;
  border-radius: 4px;
}

.filter-form {
  margin: 0;
}

.prompt-management {
  padding: 16px 0;
}

.prompt-actions {
  margin-bottom: 16px;
}
</style>
