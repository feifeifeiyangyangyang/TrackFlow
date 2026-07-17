<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">处理任务</h2>
        <p class="page-subtitle">查看事件处理任务的抢占、重试和失败原因，用来验证 RabbitMQ + Outbox 链路。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="status" clearable placeholder="任务状态" style="width: 180px" @change="load">
          <el-option label="PENDING" value="PENDING" />
          <el-option label="RUNNING" value="RUNNING" />
          <el-option label="RETRY_WAIT" value="RETRY_WAIT" />
          <el-option label="SUCCESS" value="SUCCESS" />
          <el-option label="FAILED" value="FAILED" />
        </el-select>
      </div>

      <div class="table-wrap">
        <el-table :data="rows" v-loading="loading" empty-text="暂无任务">
          <el-table-column prop="id" label="任务ID" min-width="90" />
          <el-table-column prop="raw_event_id" label="Raw Event" min-width="110" />
          <el-table-column prop="status" label="状态" min-width="120">
            <template #default="scope">
              <el-tag :type="taskType(scope.row.status)">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="重试次数" min-width="100">
            <template #default="scope">{{ scope.row.retry_count }} / {{ scope.row.max_retry_count }}</template>
          </el-table-column>
          <el-table-column prop="next_retry_time" label="下次重试" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.next_retry_time) }}</template>
          </el-table-column>
          <el-table-column prop="locked_by" label="执行者" min-width="180" show-overflow-tooltip />
          <el-table-column prop="last_error" label="错误" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" fixed="right" width="120">
            <template #default="scope">
              <el-button size="small" :disabled="scope.row.status !== 'FAILED'" @click="retry(scope.row.id)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { http } from '../api/http'
import { formatTime } from '../utils/status'

const rows = ref<any[]>([])
const loading = ref(false)
const status = ref('')

function taskType(value: string) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'RETRY_WAIT') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/event-tasks', { params: { status: status.value } })
  } finally {
    loading.value = false
  }
}

async function retry(id: number) {
  await http.post('/event-tasks/' + id + '/retry')
  ElMessage.success('任务已重新入队')
  await load()
}

onMounted(load)
</script>
