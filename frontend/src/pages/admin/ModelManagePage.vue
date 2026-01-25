<template>
  <div id="modelManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词">
        <a-input v-model:value="searchParams.searchText" placeholder="模型名称或ID" />
      </a-form-item>
      <a-form-item label="提供商">
        <a-select
          v-model:value="searchParams.provider"
          placeholder="选择提供商"
          style="width: 150px"
          allowClear
        >
          <a-select-option value="">全部</a-select-option>
          <a-select-option v-for="p in providers" :key="p" :value="p">{{ p }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
        <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
      </a-form-item>
    </a-form>
    <a-divider />

    <!-- 统计信息 -->
    <div class="stats-row">
      <a-statistic title="模型总数" :value="total" />
      <a-statistic title="总Tokens使用量">
        <template #formatter>
          {{ formatTokens(totalTokensSum) }}
        </template>
      </a-statistic>
      <a-statistic title="总花费 (USD)">
        <template #formatter>
          ${{ formatCost(totalCostSum) }}
        </template>
      </a-statistic>
    </div>
    <a-divider />

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :loading="loading"
      @change="doTableChange"
      :scroll="{ x: 1500, y: 'calc(100vh - 350px)' }"
      rowKey="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'id'">
          <a-tooltip :title="record.id">
            <span class="model-id">{{ record.id }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'name'">
          <div class="model-name-cell">
            <img
              :src="getProviderIcon(record.provider)"
              alt=""
              class="provider-icon"
            />
            <span>{{ record.name }}</span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'provider'">
          <a-tag color="blue">{{ record.provider }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'recommended'">
          <a-tag v-if="record.recommended" color="green">推荐</a-tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.dataIndex === 'isChina'">
          <a-tag v-if="record.isChina" color="red">国内</a-tag>
          <a-tag v-else color="default">国外</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'contextLength'">
          {{ formatNumber(record.contextLength) }}
        </template>
        <template v-else-if="column.dataIndex === 'inputPrice'">
          ${{ record.inputPrice?.toFixed(4) || '0.0000' }}
        </template>
        <template v-else-if="column.dataIndex === 'outputPrice'">
          ${{ record.outputPrice?.toFixed(4) || '0.0000' }}
        </template>
        <template v-else-if="column.dataIndex === 'userTotalTokens'">
          <span :class="{ 'highlight-value': Number(record.userTotalTokens) > 0 }">
            {{ formatTokens(Number(record.userTotalTokens) || 0) }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'userTotalCost'">
          <span :class="{ 'highlight-value': Number(record.userTotalCost) > 0 }">
            ${{ formatCostDetail(Number(record.userTotalCost) || 0) }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'tags'">
          <div class="tags-cell">
            <a-tag v-for="tag in (record.tags || []).slice(0, 3)" :key="tag" size="small">
              {{ tag }}
            </a-tag>
            <a-tooltip v-if="(record.tags || []).length > 3" :title="(record.tags || []).join(', ')">
              <a-tag size="small">+{{ (record.tags || []).length - 3 }}</a-tag>
            </a-tooltip>
          </div>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { listModels } from '@/api/modelController'
import { message } from 'ant-design-vue'

const columns = [
  {
    title: '模型ID',
    dataIndex: 'id',
    width: 200,
    ellipsis: true,
  },
  {
    title: '模型名称',
    dataIndex: 'name',
    width: 180,
  },
  {
    title: '提供商',
    dataIndex: 'provider',
    width: 120,
  },
  {
    title: '国内/国外',
    dataIndex: 'isChina',
    width: 100,
  },
  {
    title: '推荐',
    dataIndex: 'recommended',
    width: 80,
  },
  {
    title: '上下文长度',
    dataIndex: 'contextLength',
    width: 120,
  },
  {
    title: '输入价格/M',
    dataIndex: 'inputPrice',
    width: 120,
  },
  {
    title: '输出价格/M',
    dataIndex: 'outputPrice',
    width: 120,
  },
  {
    title: '累计Tokens',
    dataIndex: 'userTotalTokens',
    width: 120,
    sorter: true,
  },
  {
    title: '累计花费',
    dataIndex: 'userTotalCost',
    width: 120,
    sorter: true,
  },
  {
    title: '标签',
    dataIndex: 'tags',
    width: 200,
  },
]

interface ModelData {
  id: string
  name: string
  description?: string
  provider: string
  contextLength?: number
  inputPrice?: number
  outputPrice?: number
  recommended?: boolean
  isChina?: boolean
  tags?: string[]
  totalTokens?: number
  totalCost?: number
  userTotalTokens?: number
  userTotalCost?: number
}

const data = ref<ModelData[]>([])
const total = ref(0)
const loading = ref(false)
const providers = ref<string[]>([])

const searchParams = reactive({
  pageNum: 1,
  pageSize: 20,
  searchText: '',
  provider: '',
})

const totalTokensSum = computed(() => {
  return data.value.reduce((sum, item) => {
    const tokens = Number(item.userTotalTokens) || 0
    return sum + tokens
  }, 0)
})

const totalCostSum = computed(() => {
  return data.value.reduce((sum, item) => {
    const cost = Number(item.userTotalCost) || 0
    return sum + cost
  }, 0)
})

const formatTokens = (num: number) => {
  if (!num || isNaN(num)) return '0'
  return num.toLocaleString('zh-CN')
}

const formatCost = (num: number) => {
  if (!num || isNaN(num)) return '0.0000'
  return num.toFixed(4)
}

const formatCostDetail = (num: number) => {
  if (!num || isNaN(num)) return '0.000000'
  return num.toFixed(6)
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listModels({
      pageNum: searchParams.pageNum,
      pageSize: searchParams.pageSize,
      searchText: searchParams.searchText || undefined,
      provider: searchParams.provider || undefined,
    })
    if (res.data?.data) {
      data.value = res.data.data.records ?? []
      total.value = res.data.data.totalRow ?? 0

      // 提取所有提供商用于筛选
      const providerSet = new Set<string>()
      data.value.forEach((item) => {
        if (item.provider) {
          providerSet.add(item.provider)
        }
      })
      if (providers.value.length === 0) {
        providers.value = Array.from(providerSet).sort()
      }
    } else {
      message.error('获取数据失败，' + res.data?.message)
    }
  } catch (error) {
    console.error('获取模型列表失败:', error)
    message.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

const pagination = computed(() => {
  return {
    current: searchParams.pageNum,
    pageSize: searchParams.pageSize,
    total: total.value,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (t: number) => `共 ${t} 个模型`,
    pageSizeOptions: ['10', '20', '50', '100'],
  }
})

const doTableChange = (pag: any) => {
  if (pag) {
    searchParams.pageNum = pag.current || 1
    searchParams.pageSize = pag.pageSize || 20
    fetchData()
  }
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const handleReset = () => {
  searchParams.searchText = ''
  searchParams.provider = ''
  searchParams.pageNum = 1
  fetchData()
}

const formatNumber = (num: number | undefined) => {
  if (num === undefined || num === null) return '-'
  return num.toLocaleString()
}

const getProviderIcon = (provider: string) => {
  const providerLower = provider?.toLowerCase() || ''
  const iconMap: Record<string, string> = {
    openai: 'openai.png',
    anthropic: 'anthropic.png',
    google: 'google.png',
    'meta-llama': 'meta-llama.png',
    qwen: 'qwen.png',
    alibaba: 'alibaba.png',
    deepseek: 'deepseek.png',
    baidu: 'baidu.png',
    zhipu: 'zhipu.png',
    moonshot: 'moonshot.png',
    tencent: 'tencent.png',
    bytedance: 'bytedance.png',
    meituan: 'meituan.png',
  }
  const iconFile = iconMap[providerLower] || 'default.png'
  return new URL(`../../assets/provider-icons/${iconFile}`, import.meta.url).href
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#modelManagePage {
  padding: 24px;
  background: white;
  height: 100%;
  overflow-y: auto;
}

.stats-row {
  display: flex;
  gap: 48px;
  margin-bottom: 16px;
}

.model-id {
  font-family: monospace;
  font-size: 12px;
  color: #666;
}

.model-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.provider-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  object-fit: contain;
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.highlight-value {
  color: #1890ff;
  font-weight: 500;
}
</style>
