<template>
  <a-modal
    v-model:open="visible"
    title="编辑个人信息"
    :width="480"
    :destroyOnClose="true"
    centered
    @ok="handleSubmit"
    @cancel="handleCancel"
    :confirmLoading="loading"
    okText="保存"
    cancelText="取消"
  >
    <a-form
      ref="formRef"
      :model="formState"
      :label-col="{ span: 5 }"
      :wrapper-col="{ span: 18 }"
      class="edit-form"
    >
      <!-- 头像 -->
      <a-form-item label="头像">
        <div class="avatar-upload">
          <div class="avatar-wrapper">
            <a-avatar :src="formState.userAvatar || defaultAvatar" :size="80" />
            <a-upload
              :show-upload-list="false"
              :before-upload="handleAvatarUpload"
              accept="image/*"
            >
              <div class="avatar-overlay">
                <CameraOutlined />
              </div>
            </a-upload>
            <a-spin v-if="avatarUploading" class="avatar-loading" />
          </div>
          <div class="avatar-tips">点击头像上传</div>
        </div>
      </a-form-item>

      <!-- 昵称 -->
      <a-form-item
        label="昵称"
        name="userName"
        :rules="[{ required: true, message: '请输入昵称' }]"
      >
        <a-input
          v-model:value="formState.userName"
          placeholder="请输入昵称"
          :maxlength="20"
          show-count
        />
      </a-form-item>

      <!-- 个人简介 -->
      <a-form-item label="个人简介" name="userProfile">
        <a-textarea
          v-model:value="formState.userProfile"
          placeholder="请输入个人简介"
          :rows="3"
          :maxlength="200"
          show-count
        />
      </a-form-item>

      <a-divider>预算设置</a-divider>

      <!-- 日预算 -->
      <a-form-item label="日预算" name="dailyBudget">
        <a-input-number
          v-model:value="formState.dailyBudget"
          placeholder="不限制"
          :min="0"
          :max="10000"
          :precision="2"
          :step="0.1"
          style="width: 100%"
        >
          <template #addonAfter>USD</template>
        </a-input-number>
        <div class="field-tips">每日API调用成本上限，留空表示不限制</div>
      </a-form-item>

      <!-- 月预算 -->
      <a-form-item label="月预算" name="monthlyBudget">
        <a-input-number
          v-model:value="formState.monthlyBudget"
          placeholder="不限制"
          :min="0"
          :max="100000"
          :precision="2"
          :step="1"
          style="width: 100%"
        >
          <template #addonAfter>USD</template>
        </a-input-number>
        <div class="field-tips">每月API调用成本上限，留空表示不限制</div>
      </a-form-item>

      <!-- 预警阈值 -->
      <a-form-item label="预警阈值" name="budgetAlertThreshold">
        <a-slider
          v-model:value="formState.budgetAlertThreshold"
          :min="50"
          :max="100"
          :marks="{ 50: '50%', 80: '80%', 100: '100%' }"
        />
        <div class="field-tips">当预算使用达到此比例时会显示预警提示</div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance, UploadProps } from 'ant-design-vue'
import { CameraOutlined } from '@ant-design/icons-vue'
import { updateMyInfo } from '@/api/userController'
import { uploadImage } from '@/api/fileController'
import { useLoginUserStore } from '@/stores/loginUser'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const loginUserStore = useLoginUserStore()
const visible = ref(props.open)
const loading = ref(false)
const avatarUploading = ref(false)
const formRef = ref<FormInstance>()

const defaultAvatar = 'https://api.dicebear.com/7.x/avataaars/svg?seed=default'

const handleAvatarUpload: UploadProps['beforeUpload'] = async (file) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件')
    return false
  }

  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
    return false
  }

  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadImage(formData)
    if (res.data.code === 0 && res.data.data?.url) {
      formState.userAvatar = res.data.data.url
      message.success('头像上传成功')
    } else {
      message.error('头像上传失败：' + res.data.message)
    }
  } catch (error) {
    message.error('头像上传失败，请稍后重试')
  } finally {
    avatarUploading.value = false
  }

  return false
}

const formState = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
  dailyBudget: null as number | null,
  monthlyBudget: null as number | null,
  budgetAlertThreshold: 80,
})

watch(
  () => props.open,
  (newVal) => {
    visible.value = newVal
    if (newVal) {
      // 打开弹窗时，填充当前用户信息
      const user = loginUserStore.loginUser
      formState.userName = user.userName || ''
      formState.userAvatar = user.userAvatar || ''
      formState.userProfile = user.userProfile || ''
      formState.dailyBudget = user.dailyBudget || null
      formState.monthlyBudget = user.monthlyBudget || null
      formState.budgetAlertThreshold = user.budgetAlertThreshold || 80
    }
  }
)

watch(visible, (newVal) => {
  emit('update:open', newVal)
})

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await updateMyInfo({
      userName: formState.userName,
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile,
      dailyBudget: formState.dailyBudget,
      monthlyBudget: formState.monthlyBudget,
      budgetAlertThreshold: formState.budgetAlertThreshold,
    })

    if (res.data.code === 0) {
      message.success('保存成功')
      // 刷新用户信息
      await loginUserStore.fetchLoginUser()
      visible.value = false
      emit('success')
    } else {
      message.error('保存失败：' + res.data.message)
    }
  } catch (error) {
    message.error('保存失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  visible.value = false
}
</script>

<style scoped>
.edit-form {
  padding: 16px 0;
}

.avatar-upload {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  cursor: pointer;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  opacity: 0;
  transition: opacity 0.3s;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.avatar-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.avatar-tips {
  font-size: 12px;
  color: #999;
}

.field-tips {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
