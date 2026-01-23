<script setup lang="ts">
import { useLoginUserStore } from '@/stores/loginUser'
import { useRouter } from 'vue-router'
import { SwapOutlined, ExperimentOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()

const navigateTo = (path: string) => {
  router.push(path)
}
</script>

<template>
  <div id="homePage">
    <div class="container">
      <!-- 网站标题和描述 -->
      <div class="hero-section">
        <h1 class="hero-title">AI模型评测平台</h1>
        <p class="hero-description">对比测试100+大模型，找到最适合你的AI助手</p>
      </div>

      <!-- 欢迎信息 -->
      <div class="welcome-section">
        <a-card class="welcome-card">
          <template #title>
            <span>👋 欢迎使用</span>
          </template>
          <p v-if="loginUserStore.loginUser.id">
            欢迎回来，{{ loginUserStore.loginUser.userName }}！开始你的AI模型评测之旅吧。
          </p>
          <p v-else>
            请先 <router-link to="/user/login">登录</router-link> 或
            <router-link to="/user/register">注册</router-link> 开始使用
          </p>
        </a-card>
      </div>

      <!-- 核心功能入口 -->
      <div class="features-section">
        <h2 class="section-title">核心功能</h2>
        <a-row :gutter="[24, 24]">
          <a-col :xs="24" :sm="12">
            <a-card class="feature-card clickable" @click="navigateTo('/side-by-side')">
              <template #title>
                <SwapOutlined style="margin-right: 8px" />
                Side-by-Side 并排对比
              </template>
              <p>选择1-8个模型，并排对比回答效果、速度和成本</p>
              <div class="feature-tags">
                <a-tag color="blue">实时流式</a-tag>
                <a-tag color="green">并行调用</a-tag>
                <a-tag color="orange">性能统计</a-tag>
              </div>
            </a-card>
          </a-col>
          <a-col :xs="24" :sm="12">
            <a-card class="feature-card clickable" @click="navigateTo('/prompt-lab')">
              <template #title>
                <ExperimentOutlined style="margin-right: 8px" />
                Prompt Lab 提示词实验
              </template>
              <p>测试同一模型的不同提示词策略，优化提示词效果</p>
              <div class="feature-tags">
                <a-tag color="purple">提示词优化</a-tag>
                <a-tag color="cyan">效果对比</a-tag>
                <a-tag color="geekblue">最佳推荐</a-tag>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </div>

      <!-- 平台特性 -->
      <div class="features-section">
        <h2 class="section-title">平台特性</h2>
        <a-row :gutter="[24, 24]">
          <a-col :xs="24" :sm="12" :md="8">
            <a-card class="feature-card">
              <template #title>🚀 337+模型</template>
              <p>支持OpenAI、Claude、Gemini、通义千问、DeepSeek等337个模型</p>
            </a-card>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8">
            <a-card class="feature-card">
              <template #title>⚡ 并行调用</template>
              <p>多模型并行调用，响应速度提升4倍</p>
            </a-card>
          </a-col>
          <a-col :xs="24" :sm="12" :md="8">
            <a-card class="feature-card">
              <template #title>💰 成本透明</template>
              <p>实时Token统计，精确成本计算</p>
            </a-card>
          </a-col>
        </a-row>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 50%, #e2e8f0 100%);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

/* 英雄区域 */
.hero-section {
  text-align: center;
  padding: 80px 0 60px;
  margin-bottom: 28px;
}

.hero-title {
  font-size: 48px;
  font-weight: 700;
  margin: 0 0 20px;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-description {
  font-size: 20px;
  margin: 0;
  color: #64748b;
}

/* 欢迎区域 */
.welcome-section {
  margin-bottom: 40px;
}

.welcome-card {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
}

/* 功能介绍区域 */
.features-section {
  margin-bottom: 60px;
}

.section-title {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 24px;
  color: #1e293b;
  text-align: center;
}

.feature-card {
  height: 100%;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  transition: transform 0.3s, box-shadow 0.3s;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.1);
}

.feature-card.clickable {
  cursor: pointer;
}

.feature-tags {
  margin-top: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-description {
    font-size: 16px;
  }
}
</style>
