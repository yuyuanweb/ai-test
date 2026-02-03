<template>
  <a-modal
    v-model:open="visible"
    title="预算设置"
    :confirm-loading="loading"
    @ok="handleSubmit"
    @cancel="handleCancel"
    :width="500"
  >
    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="日预算限额">
        <a-input-number
          v-model:value="formData.dailyBudget"
          :min="0"
          :max="1000"
          :precision="2"
          :step="0.1"
          addon-before="$"
          placeholder="留空表示不限制"
          style="width: 100%"
        />
        <div class="form-tip">设置每日最大消耗金额(USD)，达到限额将无法继续调用</div>
      </a-form-item>

      <a-form-item label="月预算限额">
        <a-input-number
          v-model:value="formData.monthlyBudget"
          :min="0"
          :max="10000"
          :precision="2"
          :step="1"
          addon-before="$"
          placeholder="留空表示不限制"
          style="width: 100%"
        />
        <div class="form-tip">设置每月最大消耗金额(USD)，达到限额将无法继续调用</div>
      </a-form-item>

      <a-form-item label="预警阈值">
        <a-input-number
          v-model:value="formData.alertThreshold"
          :min="1"
          :max="99"
          addon-after="%"
          placeholder="默认80"
          style="width: 100%"
        />
        <div class="form-tip">当消耗达到预算的该百分比时显示预警</div>
      </a-form-item>

      <a-divider />

      <div class="current-status">
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="今日已消耗">
            <span :class="getDailyStatusClass()">
              ${{ budgetStatus?.todayCost?.toFixed(4) || '0.0000' }}
            </span>
            <span v-if="budgetStatus?.dailyBudget" class="budget-limit">
              / ${{ budgetStatus.dailyBudget.toFixed(2) }}
              ({{ budgetStatus.dailyUsagePercent?.toFixed(0) || 0 }}%)
            </span>
          </a-descriptions-item>
          <a-descriptions-item label="本月已消耗">
            <span :class="getMonthlyStatusClass()">
              ${{ budgetStatus?.monthCost?.toFixed(4) || '0.0000' }}
            </span>
            <span v-if="budgetStatus?.monthlyBudget" class="budget-limit">
              / ${{ budgetStatus.monthlyBudget.toFixed(2) }}
              ({{ budgetStatus.monthlyUsagePercent?.toFixed(0) || 0 }}%)
            </span>
          </a-descriptions-item>
          <a-descriptions-item label="当前状态">
            <a-tag :color="getStatusColor()">{{ getStatusText() }}</a-tag>
          </a-descriptions-item>
        </a-descriptions>
      </div>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { updateBudget, getBudgetStatus } from '@/api/userController'

interface Props {
  open: boolean
  initialData?: {
    dailyBudget?: number
    monthlyBudget?: number
    alertThreshold?: number
  }
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const visible = ref(false)
const loading = ref(false)
const budgetStatus = ref<API.BudgetStatusVO | null>(null)

const formData = ref<API.BudgetUpdateRequest>({
  dailyBudget: undefined,
  monthlyBudget: undefined,
  alertThreshold: 80,
})

watch(
  () => props.open,
  (newVal) => {
    visible.value = newVal
    if (newVal) {
      initFormData()
      loadBudgetStatus()
    }
  }
)

watch(visible, (newVal) => {
  emit('update:open', newVal)
})

const initFormData = () => {
  if (props.initialData) {
    formData.value = {
      dailyBudget: props.initialData.dailyBudget || undefined,
      monthlyBudget: props.initialData.monthlyBudget || undefined,
      alertThreshold: props.initialData.alertThreshold || 80,
    }
  }
}

const loadBudgetStatus = async () => {
  try {
    const res = await getBudgetStatus()
    if (res.data.code === 0) {
      budgetStatus.value = res.data.data || null
    }
  } catch (error) {
    console.error('获取预算状态失败', error)
  }
}

const handleSubmit = async () => {
  loading.value = true
  try {
    const res = await updateBudget(formData.value)
    if (res.data.code === 0) {
      message.success('预算设置已更新')
      emit('success')
      visible.value = false
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch (error) {
    message.error('更新预算设置失败')
    console.error('更新预算设置失败', error)
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  visible.value = false
}

const getDailyStatusClass = () => {
  if (!budgetStatus.value?.dailyBudget) return ''
  const percent = budgetStatus.value.dailyUsagePercent || 0
  if (percent >= 100) return 'status-exceeded'
  if (percent >= 80) return 'status-warning'
  return 'status-normal'
}

const getMonthlyStatusClass = () => {
  if (!budgetStatus.value?.monthlyBudget) return ''
  const percent = budgetStatus.value.monthlyUsagePercent || 0
  if (percent >= 100) return 'status-exceeded'
  if (percent >= 80) return 'status-warning'
  return 'status-normal'
}

const getStatusColor = () => {
  const status = budgetStatus.value?.status
  if (status === 'exceeded') return 'error'
  if (status === 'warning') return 'warning'
  return 'success'
}

const getStatusText = () => {
  const status = budgetStatus.value?.status
  if (status === 'exceeded') return '已超出预算'
  if (status === 'warning') return '接近预算上限'
  return '预算充足'
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.current-status {
  margin-top: 16px;
}

.budget-limit {
  color: #999;
  margin-left: 8px;
}

.status-normal {
  color: #52c41a;
  font-weight: 500;
}

.status-warning {
  color: #faad14;
  font-weight: 500;
}

.status-exceeded {
  color: #ff4d4f;
  font-weight: 500;
}
</style>
