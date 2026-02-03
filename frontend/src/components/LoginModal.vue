<template>
  <a-modal
    v-model:open="visible"
    :footer="null"
    :width="420"
    :destroyOnClose="true"
    centered
  >
    <div class="login-modal">
      <!-- 登录表单 -->
      <template v-if="activeTab === 'login'">
        <h2 class="modal-title">用户登录</h2>
        <a-form
          :model="loginForm"
          name="loginForm"
          autocomplete="off"
          @finish="handleLogin"
        >
          <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input
              v-model:value="loginForm.userAccount"
              placeholder="请输入账号"
              size="large"
            >
              <template #prefix>
                <UserOutlined style="color: rgba(0, 0, 0, 0.25)" />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="loginForm.userPassword"
              placeholder="请输入密码"
              size="large"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              :loading="loading"
              block
              size="large"
              class="submit-btn"
            >
              登 录
            </a-button>
          </a-form-item>
        </a-form>
        <div class="switch-tip">
          没有账号？
          <a @click="switchTab('register')">立即注册</a>
        </div>
      </template>

      <!-- 注册表单 -->
      <template v-else>
        <h2 class="modal-title">用户注册</h2>
        <a-form
          :model="registerForm"
          name="registerForm"
          autocomplete="off"
          @finish="handleRegister"
        >
          <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input
              v-model:value="registerForm.userAccount"
              placeholder="请输入账号"
              size="large"
            >
              <template #prefix>
                <UserOutlined style="color: rgba(0, 0, 0, 0.25)" />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码不能小于 8 位' },
            ]"
          >
            <a-input-password
              v-model:value="registerForm.userPassword"
              placeholder="请输入密码"
              size="large"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item
            name="checkPassword"
            :rules="[
              { required: true, message: '请确认密码' },
              { min: 8, message: '密码不能小于 8 位' },
              { validator: validateCheckPassword },
            ]"
          >
            <a-input-password
              v-model:value="registerForm.checkPassword"
              placeholder="请确认密码"
              size="large"
            >
              <template #prefix>
                <LockOutlined style="color: rgba(0, 0, 0, 0.25)" />
              </template>
            </a-input-password>
          </a-form-item>
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              :loading="loading"
              block
              size="large"
              class="submit-btn"
            >
              注 册
            </a-button>
          </a-form-item>
        </a-form>
        <div class="switch-tip">
          已有账号？
          <a @click="switchTab('login')">立即登录</a>
        </div>
      </template>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { userLogin, userRegister } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { useLoginModalStore } from '@/stores/loginModal'

const loginUserStore = useLoginUserStore()
const loginModalStore = useLoginModalStore()

const visible = computed({
  get: () => loginModalStore.visible,
  set: (val) => {
    if (val) {
      loginModalStore.openModal()
    } else {
      loginModalStore.closeModal()
    }
  },
})

const activeTab = computed({
  get: () => loginModalStore.activeTab,
  set: (val) => {
    loginModalStore.activeTab = val
  },
})

const loading = ref(false)

const loginForm = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const registerForm = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

watch(visible, (newVal) => {
  if (!newVal) {
    resetForms()
  }
})

const resetForms = () => {
  loginForm.userAccount = ''
  loginForm.userPassword = ''
  registerForm.userAccount = ''
  registerForm.userPassword = ''
  registerForm.checkPassword = ''
}

const switchTab = (tab: 'login' | 'register') => {
  loginModalStore.activeTab = tab
  resetForms()
}

const validateCheckPassword = (
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void
) => {
  if (value && value !== registerForm.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const handleLogin = async (values: API.UserLoginRequest) => {
  loading.value = true
  try {
    const res = await userLogin(values)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser()
      message.success('登录成功')
      loginModalStore.closeModal()
    } else {
      message.error('登录失败，' + res.data.message)
    }
  } catch (error) {
    message.error('登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleRegister = async (values: API.UserRegisterRequest) => {
  loading.value = true
  try {
    const res = await userRegister(values)
    if (res.data.code === 0) {
      message.success('注册成功，请登录')
      switchTab('login')
    } else {
      message.error('注册失败，' + res.data.message)
    }
  } catch (error) {
    message.error('注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-modal {
  padding: 24px 12px;
}

.modal-title {
  text-align: center;
  margin-bottom: 32px;
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
}

.submit-btn {
  background: #000 !important;
  border-color: #000 !important;
  height: 44px;
  font-size: 16px;
}

.submit-btn:hover {
  background: #333 !important;
  border-color: #333 !important;
}

.switch-tip {
  text-align: center;
  color: #999;
  font-size: 14px;
}

.switch-tip a {
  color: #1890ff;
  cursor: pointer;
}

.switch-tip a:hover {
  color: #40a9ff;
}
</style>
