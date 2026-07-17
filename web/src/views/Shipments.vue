<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">运单管理</h2>
        <p class="page-subtitle">检索运单、查看可信状态，并进入详情分析完整事件时间线。</p>
      </div>
      <el-button type="primary" @click="createDemo">创建演示运单</el-button>
    </div>

    <div class="panel">
      <div class="toolbar" role="search">
        <el-input
          v-model="q"
          clearable
          placeholder="搜索运单号或业务订单号"
          style="width: 320px"
          aria-label="搜索运单号或业务订单号"
          @keyup.enter="load"
        />
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <div class="table-wrap">
        <el-table :data="rows" v-loading="loading" empty-text="暂无运单" @row-click="open">
          <el-table-column prop="tracking_no" label="运单号" min-width="160" />
          <el-table-column prop="business_order_no" label="业务订单" min-width="160" />
          <el-table-column prop="carrier_code" label="物流商" min-width="120" />
          <el-table-column label="当前状态" min-width="150">
            <template #default="scope">
              <el-tag :type="statusType(scope.row.current_status)">
                {{ statusText[scope.row.current_status] || scope.row.current_status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="异常" min-width="110">
            <template #default="scope">
              <el-tag :type="scope.row.has_open_anomaly ? 'danger' : 'success'">
                {{ scope.row.has_open_anomaly ? '有异常' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updated_at" label="更新时间" min-width="180">
            <template #default="scope">{{ formatTime(scope.row.updated_at) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-drawer v-model="show" size="min(760px, 92vw)" title="运单详情">
      <template v-if="detail.shipment">
        <div class="section-header">
          <div>
            <h3>{{ detail.shipment.tracking_no }}</h3>
            <p class="page-subtitle">{{ detail.shipment.business_order_no }} · {{ detail.shipment.carrier_code }}</p>
          </div>
          <el-button type="warning" :loading="reconciling" @click="reconcile">发起对账</el-button>
        </div>

        <div class="detail-summary">
          <div class="summary-item">
            <span>当前可信状态</span>
            <strong>{{ statusText[detail.shipment.current_status] || detail.shipment.current_status }}</strong>
          </div>
          <div class="summary-item">
            <span>状态发生时间</span>
            <strong>{{ formatTime(detail.shipment.current_status_event_time) }}</strong>
          </div>
          <div class="summary-item">
            <span>最近接收时间</span>
            <strong>{{ formatTime(detail.shipment.last_received_time) }}</strong>
          </div>
          <div class="summary-item">
            <span>异常状态</span>
            <strong>{{ detail.shipment.has_open_anomaly ? '存在待处理异常' : '无开放异常' }}</strong>
          </div>
        </div>

        <div class="timeline" aria-label="事件时间线">
          <div
            v-for="event in detail.events"
            :key="event.id"
            class="timeline-item"
            :class="{ invalid: event.validation_status !== 'VALID', reconciliation: event.source === 'RECONCILIATION' }"
          >
            <strong>{{ statusText[event.normalized_status] || event.normalized_status }} / {{ event.raw_status }}</strong>
            <div class="timeline-meta">
              <span>eventTime {{ formatTime(event.event_time) }}</span>
              <span>receivedTime {{ formatTime(event.received_time) }}</span>
              <span>{{ event.location || '未知地点' }}</span>
            </div>
            <div class="toolbar">
              <el-tag v-if="event.late_arrival" type="warning">迟到事件</el-tag>
              <el-tag v-if="event.validation_status !== 'VALID'" type="danger">{{ event.validation_status }}</el-tag>
              <el-tag v-if="event.source === 'RECONCILIATION'" type="success">对账补入</el-tag>
              <el-tag v-if="!event.applied_to_state" type="info">未参与状态计算</el-tag>
            </div>
            <p class="muted">{{ event.description || '无描述' }}</p>
          </div>
        </div>
      </template>
      <div v-else class="empty-state">请选择一条运单查看详情</div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { http } from '../api/http'
import { formatTime, statusText, statusType } from '../utils/status'

const q = ref('')
const rows = ref<any[]>([])
const loading = ref(false)
const show = ref(false)
const reconciling = ref(false)
const detail = ref<any>({})

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/shipments', { params: { q: q.value } })
  } finally {
    loading.value = false
  }
}

function reset() {
  q.value = ''
  load()
}

async function open(row: any) {
  detail.value = await http.get('/shipments/' + row.id)
  show.value = true
}

async function createDemo() {
  const suffix = Date.now()
  await http.post('/shipments', { carrierCode: 'MOCK_A', trackingNo: 'A' + suffix, businessOrderNo: 'ORDER-' + suffix })
  ElMessage.success('演示运单已创建')
  await load()
}

async function reconcile() {
  reconciling.value = true
  try {
    await http.post('/reconciliation/shipments/' + detail.value.shipment.id)
    detail.value = await http.get('/shipments/' + detail.value.shipment.id)
    await load()
    ElMessage.success('对账完成，已重新计算状态')
  } finally {
    reconciling.value = false
  }
}

onMounted(load)
</script>
