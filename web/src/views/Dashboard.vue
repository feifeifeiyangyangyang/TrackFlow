<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">履约数据总览</h2>
        <p class="page-subtitle">聚合运单状态、异常分布和最近事件，帮助运营快速判断履约风险。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新数据</el-button>
    </div>

    <el-alert v-if="error" type="error" :title="error" show-icon class="panel" />

    <div class="metric-grid" v-loading="loading" aria-label="核心指标">
      <div v-for="m in metrics" :key="m.label" class="metric">
        <span>{{ m.label }}</span>
        <b>{{ m.value }}</b>
        <small>{{ m.hint }}</small>
      </div>
    </div>

    <div class="panel">
      <div class="section-header">
        <div>
          <h3>状态分布</h3>
          <p class="page-subtitle">当前可信状态由事件时间线重放得到。</p>
        </div>
      </div>
      <div v-if="statusDistribution.length" class="metric-grid">
        <div v-for="row in statusDistribution" :key="row.status" class="summary-item">
          <span>{{ statusText[row.status] || row.status }}</span>
          <strong>{{ row.total }}</strong>
        </div>
      </div>
      <div v-else class="empty-state">暂无状态分布数据</div>
    </div>

    <div class="panel">
      <div class="section-header">
        <div>
          <h3>最近事件</h3>
          <p class="page-subtitle">展示最新标准化事件，包括来源、状态和业务发生时间。</p>
        </div>
      </div>
      <div class="table-wrap">
        <el-table :data="data.recentEvents || []" empty-text="暂无事件">
          <el-table-column prop="tracking_no" label="运单号" min-width="150" />
          <el-table-column prop="normalized_status" label="统一状态" min-width="150">
            <template #default="scope">
              <el-tag :type="statusType(scope.row.normalized_status)">
                {{ statusText[scope.row.normalized_status] || scope.row.normalized_status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="event_time" label="发生时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.event_time) }}</template>
          </el-table-column>
          <el-table-column prop="source" label="来源" min-width="130" />
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../api/http'
import { formatTime, statusText, statusType } from '../utils/status'

const data = ref<any>({})
const loading = ref(false)
const error = ref('')

const metrics = computed(() => [
  { label: '运单总数', value: data.value.shipmentTotal ?? 0, hint: '全部纳入治理的运单' },
  { label: '运输中', value: data.value.inTransit ?? 0, hint: '仍在流转中的运单' },
  { label: '已签收', value: data.value.delivered ?? 0, hint: '终态为已签收' },
  { label: '异常运单', value: data.value.openAnomalies ?? 0, hint: '待处理异常数量' },
  { label: '今日事件', value: data.value.todayEvents ?? 0, hint: '今日标准化事件' },
  { label: '今日差异', value: data.value.todayDifferences ?? 0, hint: '对账发现的差异' },
])

const statusDistribution = computed(() => data.value.statusDistribution || [])

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await http.get('/dashboard')
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
