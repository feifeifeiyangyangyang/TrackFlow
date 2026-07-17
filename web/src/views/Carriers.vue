<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">物流商配置</h2>
        <p class="page-subtitle">查看已接入的 Mock 物流商和轨迹查询地址，Webhook 密钥不会在前端明文展示。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <div class="table-wrap">
        <el-table :data="rows" v-loading="loading" empty-text="暂无物流商">
          <el-table-column prop="carrier_code" label="编码" min-width="120" />
          <el-table-column prop="carrier_name" label="名称" min-width="160" />
          <el-table-column prop="query_base_url" label="查询地址" min-width="280" show-overflow-tooltip />
          <el-table-column label="启用" min-width="100">
            <template #default="scope">
              <el-switch :model-value="scope.row.enabled" disabled aria-label="是否启用" />
            </template>
          </el-table-column>
          <el-table-column label="密钥状态" min-width="120">
            <template #default>
              <el-tag type="info">已隐藏</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '../api/http'

const rows = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/carriers')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
