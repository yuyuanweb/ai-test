import { createRouter, createWebHistory } from 'vue-router'
import ArenaLayout from '@/layouts/ArenaLayout.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import ChatPage from '@/pages/ChatPage.vue'
import SideBySidePage from '@/pages/SideBySidePage.vue'
import PromptLabPage from '@/pages/PromptLabPage.vue'
import CodeModePage from '@/pages/CodeModePage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/side-by-side',
    },
    {
      path: '/',
      component: ArenaLayout,
      children: [
        {
          path: 'side-by-side',
          name: 'Side-by-Side对比',
          component: SideBySidePage,
        },
        {
          path: 'prompt-lab',
          name: 'Prompt Lab',
          component: PromptLabPage,
        },
      ],
    },
    {
      path: '/chat',
      name: 'Chat',
      component: ChatPage,
    },
    {
      path: '/code-mode',
      name: '代码模式',
      component: CodeModePage,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/admin/user',
      name: '用户管理',
      component: UserManagePage,
    },
  ],
})

export default router
