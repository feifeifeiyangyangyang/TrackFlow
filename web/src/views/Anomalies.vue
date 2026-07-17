<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">异常中心</h2>
        <p class="page-subtitle">集中处理未知状态、非法流转、对账差异等履约风险。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="panel">
      <div class="table-wrap">
        <el-table :data="rows" v-loading="loading" empty-text="暂无异常">
          <el-table-column prop="tracking_no" label="运单号" min-width="150" />
          <el-table-column prop="anomaly_type" label="异常类型" min-width="170" />
          <el-table-column prop="severity" label="严重级别" min-width="110">
            <template #default="scope">
              <el-tag :type="scope.row.severity === 'HIGH' ? 'danger' : 'warning'">{{ scope.row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rule_code" label="规则" min-width="160" />
          <el-table-column prop="status" label="状态" min-width="120" />
          <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
          <el-table-column label="操作" fixed="right" width="120">
            <template #default="scope">
              <el-button size="small" :disabled="scope.row.status === 'RESOLVED'" @click="resolve(scope.row.id)">解决</el-button>
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

const rows = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await http.get('/anomalies')
  } finally {
    loading.value = false
  }
}

async function resolve(id: number) {
  await http.patch('/anomalies/' + id, { status: 'RESOLVED', note: 'demo-operator 已处理' })
  ElMessage.success('异常已标记为解决')
  await load()
}

onMounted(load)
</script>
