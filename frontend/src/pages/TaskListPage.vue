<template>
  <div class="task-list-page">
    <a-card title="批量测试任务列表" :bordered="false">
      <template #extra>
        <a-space>
          <a-button @click="handleCompare">
            任务对比
          </a-button>
          <a-button type="primary" @click="handleCreate">
            <template #icon><PlusOutlined /></template>
            创建新任务
          </a-button>
        </a-space>
      </template>

      <!-- 筛选区域 -->
      <div class="filter-section">
        <a-form layout="inline" :model="filterForm" class="filter-form">
          <a-form-item label="关键词">
            <a-input
              v-model:value="filterForm.keyword"
              placeholder="搜索任务名称"
              allow-clear
              style="width: 200px"
              @pressEnter="handleSearch"
            />
          </a-form-item>
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
          <a-form-item label="时间范围">
            <a-range-picker
              v-model:value="dateRange"
              format="YYYY-MM-DD HH:mm:ss"
              show-time
              style="width: 400px"
            />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
              <a-button @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </div>

      <a-table
        :columns="columns"
        :data-source="tasks"
        :loading="loading"
        :pagination="pagination"
        :row-selection="rowSelection"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'progress'">
            <a-progress
              :percent="getProgressPercent(record)"
              :status="record.status === 'failed' ? 'exception' : 'active'"
              :format="() => `${record.completedSubtasks}/${record.totalSubtasks}`"
            />
          </template>
          <template v-else-if="column.key === 'models'">
            <a-tag v-for="model in getModelsList(record.models)" :key="model" style="margin-right: 4px">
              {{ model }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record.id)">
                查看
              </a-button>
              <a-button type="link" size="small" @click="handleCopyTask(record)">
                重新测试
              </a-button>
              <a-popconfirm
                title="确定要删除这个任务吗？"
                @confirm="handleDelete(record.id)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { listTasks, deleteTask, type TestTask, type TaskQueryRequest } from '@/api/batchTestController'
import { listScenes } from '@/api/sceneController'

const router = useRouter()

const tasks = ref<TestTask[]>([])
const loading = ref(false)
const categories = ref<string[]>([])
const dateRange = ref<[Dayjs, Dayjs] | null>(null)
const filterForm = reactive({
  keyword: '',
  category: ''
})
const selectedTaskIds = ref<string[]>([])

const pagination = computed(() => ({
  current: paginationState.value.current,
  pageSize: paginationState.value.pageSize,
  total: paginationState.value.total,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const paginationState = ref({
  current: 1,
  pageSize: 10,
  total: 0
})

const columns = [
  {
    title: '任务名称',
    dataIndex: 'name',
    key: 'name',
    ellipsis: true
  },
  {
    title: '状态',
    key: 'status',
    width: 100
  },
  {
    title: '进度',
    key: 'progress',
    width: 200
  },
  {
    title: '模型',
    key: 'models',
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
    width: 200,
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
      res.data.data.records?.forEach((scene: any) => {
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

const loadTasks = async () => {
  try {
    loading.value = true
    const queryRequest: TaskQueryRequest = {
      pageNum: paginationState.value.current,
      pageSize: paginationState.value.pageSize
    }

    if (filterForm.keyword) {
      queryRequest.keyword = filterForm.keyword
    }

    if (filterForm.category) {
      queryRequest.category = filterForm.category
    }

    if (dateRange.value && dateRange.value.length === 2) {
      queryRequest.startTime = dateRange.value[0].format('YYYY-MM-DD HH:mm:ss')
      queryRequest.endTime = dateRange.value[1].format('YYYY-MM-DD HH:mm:ss')
    }

    const res = await listTasks(queryRequest)

    if (res.data?.code === 0 && res.data.data) {
      tasks.value = res.data.data.records || []
      paginationState.value.total = res.data.data.totalRow || res.data.data.total || 0
    }
  } catch (error) {
    console.error('加载任务列表失败:', error)
    message.error('加载任务列表失败')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pag: any) => {
  paginationState.value.current = pag.current
  paginationState.value.pageSize = pag.pageSize
  loadTasks()
}

const handleSearch = () => {
  paginationState.value.current = 1
  loadTasks()
}

const handleReset = () => {
  filterForm.keyword = ''
  filterForm.category = ''
  dateRange.value = null
  paginationState.value.current = 1
  loadTasks()
}

const handleCreate = () => {
  router.push('/batch-test/create')
}

const handleView = (taskId: string) => {
  router.push(`/batch-test/detail/${taskId}`)
}

const handleDelete = async (taskId: string) => {
  try {
    const res = await deleteTask(taskId)
    if (res.data?.code === 0) {
      message.success('删除成功')
      loadTasks()
    } else {
      message.error(res.data?.message || '删除失败')
    }
  } catch (error: any) {
    console.error('删除任务失败:', error)
    message.error(error.message || '删除任务失败')
  }
}

const handleCopyTask = (task: TestTask) => {
  router.push({
    path: '/batch-test/create',
    query: {
      copyFrom: task.id
    }
  })
}

const rowSelection = computed(() => {
  return {
    selectedRowKeys: selectedTaskIds.value,
    onChange: (selectedKeys: string[]) => {
      if (selectedKeys.length > 2) {
        message.warning('最多只能选择2个任务进行对比')
        return
      }
      selectedTaskIds.value = selectedKeys as string[]
    },
    getCheckboxProps: (record: TestTask) => ({
      disabled: record.status !== 'completed'
    })
  }
})

const handleCompare = () => {
  if (selectedTaskIds.value.length === 2) {
    router.push({
      path: '/batch-test/compare',
      query: {
        taskA: selectedTaskIds.value[0],
        taskB: selectedTaskIds.value[1]
      }
    })
  } else if (selectedTaskIds.value.length === 1) {
    message.warning('请选择2个任务进行对比')
  } else {
    router.push('/batch-test/compare')
  }
}

const getStatusColor = (status: string) => {
  const colorMap: Record<string, string> = {
    pending: 'default',
    running: 'processing',
    completed: 'success',
    failed: 'error',
    cancelled: 'warning'
  }
  return colorMap[status] || 'default'
}

const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending: '等待中',
    running: '运行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消'
  }
  return textMap[status] || status
}

const getProgressPercent = (record: TestTask) => {
  if (record.totalSubtasks === 0) return 0
  return Math.round((record.completedSubtasks / record.totalSubtasks) * 100)
}

const getModelsList = (modelsJson: string): string[] => {
  try {
    return JSON.parse(modelsJson)
  } catch {
    return []
  }
}

onMounted(() => {
  loadCategories()
  loadTasks()
})
</script>

<style scoped>
.task-list-page {
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
</style>
