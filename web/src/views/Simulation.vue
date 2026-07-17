<template>
  <section class="page fade-in">
    <div class="page-header">
      <div>
        <h2 class="page-title">故障模拟</h2>
        <p class="page-subtitle">用可重复的物流商场景验证幂等、乱序、漏推、异常和对账补偿能力。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="run">执行场景</el-button>
    </div>

    <div class="simulation-grid">
      <div class="panel">
        <div class="section-header">
          <div>
            <h3>场景控制台</h3>
            <p class="page-subtitle">建议面试演示从重复推送或乱序到达开始，效果最直观。</p>
          </div>
        </div>

        <el-form label-position="top" class="form-grid" @submit.prevent>
          <el-form-item label="物流商">
            <el-select v-model="form.carrierCode" aria-label="选择物流商">
              <el-option label="MOCK_A 标准签名格式" value="MOCK_A" />
              <el-option label="MOCK_B 差异字段格式" value="MOCK_B" />
            </el-select>
          </el-form-item>

          <el-form-item label="故障场景">
            <el-select v-model="form.scenario" aria-label="选择故障场景">
              <el-option
                v-for="scenario in scenarioOptions"
                :key="scenario.value"
                :label="scenario.label"
                :value="scenario.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="运单号">
            <el-input v-model="form.trackingNo" aria-label="输入运单号" />
          </el-form-item>

          <el-form-item label="重复次数">
            <el-input-number v-model="form.repeatCount" :min="1" :max="10" aria-label="重复次数" />
          </el-form-item>

          <el-form-item label="推送间隔">
            <el-input-number
              v-model="form.intervalMillis"
              :min="0"
              :max="5000"
              :step="100"
              aria-label="推送间隔毫秒"
            />
          </el-form-item>
        </el-form>

        <div class="scenario-card" aria-live="polite">
          <strong>{{ selectedScenario?.label }}</strong>
          <p>{{ selectedScenario?.description }}</p>
        </div>
      </div>

      <div class="panel">
        <div class="section-header">
          <div>
            <h3>执行结果</h3>
            <p class="page-subtitle">返回值可直接用于说明 mock 服务、Webhook 入库和状态重建链路。</p>
          </div>
          <el-button :disabled="!result" @click="copyResult">复制结果</el-button>
        </div>

        <el-alert v-if="error" :title="error" type="error" show-icon />

        <div v-if="result" class="detail-summary">
          <div class="summary-item">
            <span>场景</span>
            <strong>{{ result.scenario || form.scenario }}</strong>
          </div>
          <div class="summary-item">
            <span>运单号</span>
            <strong>{{ result.trackingNo || form.trackingNo }}</strong>
          </div>
          <div class="summary-item">
            <span>推送次数</span>
            <strong>{{ result.totalPushes ?? form.repeatCount }}</strong>
          </div>
          <div class="summary-item">
            <span>物流商</span>
            <strong>{{ result.carrierCode || form.carrierCode }}</strong>
          </div>
        </div>

        <pre v-if="result" class="json-view">{{ JSON.stringify(result, null, 2) }}</pre>
        <div v-else class="empty-state">配置场景后点击执行，结果会展示在这里</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { http } from '../api/http'

const scenarioOptions = [
  { value: 'NORMAL', label: 'NORMAL 正常签收', description: '按真实业务顺序推送完整物流链路，用于演示基础成功路径。' },
  { value: 'DUPLICATE_PUSH', label: 'DUPLICATE_PUSH 重复推送', description: '同一幂等键重复到达，系统应只写一次业务事件。' },
  { value: 'OUT_OF_ORDER', label: 'OUT_OF_ORDER 乱序到达', description: '后发生的事件先到达，系统按 eventTime 重放状态机。' },
  { value: 'MISSING_EVENT', label: 'MISSING_EVENT 漏推事件', description: '缺失中间节点后通过主动对账补偿，验证可靠性闭环。' },
  { value: 'RETURN_AFTER_DELIVERED', label: 'RETURN_AFTER_DELIVERED 签收后退回', description: '制造非法或冲突流转，观察异常中心记录。' },
  { value: 'UNKNOWN_STATUS', label: 'UNKNOWN_STATUS 未知状态', description: '物流商返回未识别状态码，系统保留证据并生成异常。' },
  { value: 'DELIVERY_FAILED_RECOVERY', label: 'DELIVERY_FAILED_RECOVERY 失败恢复', description: '先派送失败再恢复签收，适合说明状态机转移规则。' },
]

const form = reactive({
  carrierCode: 'MOCK_A',
  scenario: 'DUPLICATE_PUSH',
  trackingNo: 'A-DEMO-' + Date.now(),
  repeatCount: 2,
  intervalMillis: 0,
})

const loading = ref(false)
const error = ref('')
const result = ref<any>()

const selectedScenario = computed(() => scenarioOptions.find((item) => item.value === form.scenario))

async function run() {
  loading.value = true
  error.value = ''
  try {
    result.value = await http.post('/simulation/run', form)
    ElMessage.success('场景执行完成')
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function copyResult() {
  if (!result.value) return
  await navigator.clipboard.writeText(JSON.stringify(result.value, null, 2))
  ElMessage.success('结果已复制')
}
</script>
