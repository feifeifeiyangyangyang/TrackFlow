<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">原始事件</h2>
        <p class="page-subtitle">保留物流商原始 Webhook 入站记录，用于审计、排错和幂等追踪。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <div class="table-wrap">
        <el-table :data="rows" v-loading="loading" empty-text="暂无原始事件">
          <el-table-column prop="tracking_no" label="运单号" min-width="160" />
          <el-table-column prop="external_event_id" label="外部事件号" min-width="180" />
          <el-table-column prop="raw_status" label="原始状态" min-width="130" />
          <el-table-column label="验签" min-width="100">
            <template #default="scope">
              <el-tag :type="scope.row.signature_valid ? 'success' : 'danger'">
                {{ scope.row.signature_valid ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="process_status" label="处理状态" min-width="120" />
          <el-table-column prop="received_time" label="接收时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.received_time) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '../api/http'
import { formatTime } from '../utils/status'

const rows = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/raw-events')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
