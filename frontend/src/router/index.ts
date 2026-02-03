import { createRouter, createWebHistory } from 'vue-router'
const ArenaLayout = () =>
  import(/* webpackChunkName: "layout-arena" */ '@/layouts/ArenaLayout.vue')

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
          component: () =>
            import(
              /* webpackChunkName: "page-side-by-side" */ '@/pages/SideBySidePage.vue'
            ),
        },
        {
          path: 'prompt-lab',
          name: 'Prompt Lab',
          component: () =>
            import(
              /* webpackChunkName: "page-prompt-lab" */ '@/pages/PromptLabPage.vue'
            ),
        },
        {
          path: 'battle',
          name: 'Battle匿名对比',
          component: () =>
            import(
              /* webpackChunkName: "page-battle" */ '@/pages/BattlePage.vue'
            ),
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
          component: () =>
            import(
              /* webpackChunkName: "page-admin-model" */ '@/pages/admin/ModelManagePage.vue'
            ),
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
          component: () =>
            import(
              /* webpackChunkName: "page-admin-scene" */ '@/pages/admin/SceneManagePage.vue'
            ),
        },
      ],
    },
    {
      path: '/prompt-template',
      component: ArenaLayout,
      children: [
        {
          path: 'manage',
          name: '提示词模板',
          component: () =>
            import(
              /* webpackChunkName: "page-prompt-template" */ '@/pages/PromptTemplateManagePage.vue'
            ),
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
          component: () =>
            import(
              /* webpackChunkName: "page-batch-create" */ '@/pages/BatchTestPage.vue'
            ),
        },
        {
          path: 'list',
          name: '任务列表',
          component: () =>
            import(
              /* webpackChunkName: "page-batch-list" */ '@/pages/TaskListPage.vue'
            ),
        },
        {
          path: 'detail/:id',
          name: '任务详情',
          component: () =>
            import(
              /* webpackChunkName: "page-batch-detail" */ '@/pages/TaskDetailPage.vue'
            ),
        },
        {
          path: 'report/:id',
          name: '测试报告',
          component: () =>
            import(
              /* webpackChunkName: "page-batch-report" */ '@/pages/TaskReportPage.vue'
            ),
        },
        {
          path: 'compare',
          name: '任务对比',
          component: () =>
            import(
              /* webpackChunkName: "page-batch-compare" */ '@/pages/TaskComparePage.vue'
            ),
        },
      ],
    },
    {
      path: '/chat',
      name: 'Chat',
      component: () =>
        import(/* webpackChunkName: "page-chat" */ '@/pages/ChatPage.vue'),
    },
    {
      path: '/code-mode',
      name: '代码模式',
      component: () =>
        import(
          /* webpackChunkName: "page-code-mode" */ '@/pages/CodeModePage.vue'
        ),
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: () =>
        import(
          /* webpackChunkName: "page-user-login" */ '@/pages/user/UserLoginPage.vue'
        ),
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: () =>
        import(
          /* webpackChunkName: "page-user-register" */ '@/pages/user/UserRegisterPage.vue'
        ),
    },
    {
      path: '/admin/user',
      name: '用户管理',
      component: () =>
        import(
          /* webpackChunkName: "page-admin-user" */ '@/pages/admin/UserManagePage.vue'
        ),
    },
  ],
})

export default router
