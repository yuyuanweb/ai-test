import { useLoginUserStore } from '@/stores/loginUser'
import { useLoginModalStore } from '@/stores/loginModal'
import { message } from 'ant-design-vue'
import router from '@/router'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  const loginModalStore = useLoginModalStore()
  let loginUser = loginUserStore.loginUser
  // 确保页面刷新，首次加载时，能够等后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }
  const toUrl = to.fullPath
  if (toUrl.startsWith('/admin')) {
    if (!loginUser || !loginUser.id) {
      message.warning('请先登录')
      loginModalStore.openModal('login')
      next(false)
      return
    }
    if (loginUser.userRole !== 'admin') {
      message.error('没有权限')
      next('/')
      return
    }
  }
  next()
})
