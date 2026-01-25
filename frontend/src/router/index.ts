import { createRouter, createWebHistory } from 'vue-router'
import ArenaLayout from '@/layouts/ArenaLayout.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import ChatPage from '@/pages/ChatPage.vue'
import SideBySidePage from '@/pages/SideBySidePage.vue'
import PromptLabPage from '@/pages/PromptLabPage.vue'
import CodeModePage from '@/pages/CodeModePage.vue'
import BatchTestPage from '@/pages/BatchTestPage.vue'
import TaskListPage from '@/pages/TaskListPage.vue'
import TaskDetailPage from '@/pages/TaskDetailPage.vue'
import TaskReportPage from '@/pages/TaskReportPage.vue'
import TaskComparePage from '@/pages/TaskComparePage.vue'
import ModelManagePage from '@/pages/admin/ModelManagePage.vue'
import SceneManagePage from '@/pages/admin/SceneManagePage.vue'

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
      path: '/model',
      component: ArenaLayout,
      children: [
        {
          path: 'manage',
          name: '模型管理',
          component: ModelManagePage,
        },
      ],
    },
    {
      path: '/scene',
      component: ArenaLayout,
      children: [
        {
          path: 'manage',
          name: '场景管理',
          component: SceneManagePage,
        },
      ],
    },
    {
      path: '/batch-test',
      component: ArenaLayout,
      children: [
        {
          path: 'create',
          name: '创建批量测试',
          component: BatchTestPage,
        },
        {
          path: 'list',
          name: '任务列表',
          component: TaskListPage,
        },
        {
          path: 'detail/:id',
          name: '任务详情',
          component: TaskDetailPage,
        },
        {
          path: 'report/:id',
          name: '测试报告',
          component: TaskReportPage,
        },
        {
          path: 'compare',
          name: '任务对比',
          component: TaskComparePage,
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
