<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">对账任务</h2>
        <p class="page-subtitle">查看主动对账批次、异步任务状态、补偿事件数量和执行错误。</p>
      </div>
      <div class="topbar-meta">
        <span>每 5 秒自动刷新</span>
      </div>
    </div>

    <div class="panel">
      <div class="section-header">
        <div>
          <h3>对账批次</h3>
          <p class="page-subtitle">一次人工对账请求会创建一个批次，用于统计成功、失败和差异数量。</p>
        </div>
      </div>
      <div class="table-wrap">
        <el-table :data="batches" v-loading="loading" empty-text="暂无对账批次">
          <el-table-column prop="batch_no" label="批次号" min-width="180" />
          <el-table-column prop="trigger_type" label="触发方式" min-width="120" />
          <el-table-column prop="status" label="状态" min-width="120">
            <template #default="scope">
              <el-tag :type="taskType(scope.row.status)">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="total_count" label="总数" min-width="90" />
          <el-table-column prop="success_count" label="成功" min-width="90" />
          <el-table-column prop="failed_count" label="失败" min-width="90" />
          <el-table-column prop="difference_count" label="差异" min-width="90" />
          <el-table-column prop="inserted_event_count" label="补入事件" min-width="110" />
          <el-table-column prop="completed_at" label="完成时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.completed_at) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="panel">
      <div class="section-header">
        <div>
          <h3>任务明细</h3>
          <p class="page-subtitle">异步消费者会抢占任务，查询物流商完整轨迹，并补入漏推事件。</p>
        </div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <div class="table-wrap">
        <el-table :data="tasks" v-loading="loading" empty-text="暂无任务明细">
          <el-table-column prop="id" label="任务ID" min-width="90" />
          <el-table-column prop="tracking_no" label="运单号" min-width="160" />
          <el-table-column prop="carrier_code" label="物流商" min-width="110" />
          <el-table-column prop="status" label="状态" min-width="120">
            <template #default="scope">
              <el-tag :type="taskType(scope.row.status)">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试次数" min-width="100">
            <template #default="scope">{{ scope.row.retry_count }} / {{ scope.row.max_retry_count }}</template>
          </el-table-column>
          <el-table-column prop="remote_status" label="远端状态" min-width="130" />
          <el-table-column prop="after_status" label="重放后状态" min-width="130" />
          <el-table-column prop="inserted_event_count" label="补入事件" min-width="110" />
          <el-table-column prop="last_error" label="错误" min-width="240" show-overflow-tooltip />
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { http } from '../api/http'
import { formatTime } from '../utils/status'

const batches = ref<any[]>([])
const tasks = ref<any[]>([])
const loading = ref(false)
let timer: number | undefined

function taskType(value: string) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'RETRY_WAIT' || value === 'RUNNING') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const [batchRows, taskRows] = await Promise.all([
      http.get('/reconciliation/batches'),
      http.get('/reconciliation/tasks'),
    ]) as unknown as [any[], any[]]
    batches.value = batchRows
    tasks.value = taskRows
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
