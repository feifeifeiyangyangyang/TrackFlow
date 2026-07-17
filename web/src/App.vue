<template>
  <div class="app-shell">
    <aside class="sidebar" aria-label="主导航">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">TF</div>
        <div>
          <h1>TrackFlow</h1>
          <p>履约事件治理平台</p>
        </div>
      </div>

      <nav class="nav-list">
        <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="nav-item">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer">
        <span class="status-dot" aria-hidden="true"></span>
        <span>本地演示环境</span>
      </div>
    </aside>

    <main class="main-shell">
      <header class="topbar">
        <div>
          <p class="eyebrow">Operations Console</p>
          <h2>{{ currentTitle }}</h2>
        </div>
        <div class="topbar-meta" aria-label="服务端口">
          <span>Web 8001</span>
          <span>API 8002</span>
          <span>Mock 8003</span>
        </div>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const items = [
  { to: '/dashboard', label: '数据概览', icon: '⌁' },
  { to: '/shipments', label: '运单管理', icon: '◇' },
  { to: '/events', label: '原始事件', icon: '≋' },
  { to: '/anomalies', label: '异常中心', icon: '!' },
  { to: '/reconciliation', label: '对账任务', icon: '↻' },
  { to: '/carriers', label: '物流商配置', icon: '▣' },
  { to: '/simulation', label: '故障模拟', icon: '▶' },
]

const route = useRoute()
const currentTitle = computed(() => items.find((item) => item.to === route.path)?.label || 'TrackFlow')
</script>
