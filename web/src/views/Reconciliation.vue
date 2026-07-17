<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">对账任务</h2>
        <p class="page-subtitle">查看主动对账批次、补入事件数量和任务执行结果。</p>
      </div>
      <div class="topbar-meta">
        <span>每 5 秒自动刷新</span>
      </div>
    </div>

    <div class="panel">
      <div class="table-wrap">
        <el-table :data="rows" v-loading="loading" empty-text="暂无对账批次">
          <el-table-column prop="batch_no" label="批次号" min-width="180" />
          <el-table-column prop="trigger_type" label="触发方式" min-width="120" />
          <el-table-column prop="status" label="状态" min-width="120">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : 'warning'">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="total_count" label="总数" min-width="90" />
          <el-table-column prop="success_count" label="成功" min-width="90" />
          <el-table-column prop="difference_count" label="差异" min-width="90" />
          <el-table-column prop="inserted_event_count" label="补入事件" min-width="110" />
          <el-table-column prop="completed_at" label="完成时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.completed_at) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { http } from '../api/http'
import { formatTime } from '../utils/status'

const rows = ref<any[]>([])
const loading = ref(false)
let timer: number | undefined

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/reconciliation/batches')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
