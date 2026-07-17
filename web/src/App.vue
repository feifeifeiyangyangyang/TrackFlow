<template><div class="app"><aside><h1>TrackFlow</h1><p>多物流商履约事件治理平台</p><RouterLink v-for="item in items" :key="item.to" :to="item.to">{{ item.label }}</RouterLink></aside><main><header><strong>{{ currentTitle }}</strong><span>Web 8001 · API 8002 · Mock 8003</span></header><RouterView /></main></div></template>
<script setup lang="ts">
import { computed } from 'vue'; import { useRoute } from 'vue-router'
const items=[{to:'/dashboard',label:'数据概览'},{to:'/shipments',label:'运单管理'},{to:'/events',label:'原始事件'},{to:'/anomalies',label:'异常中心'},{to:'/reconciliation',label:'对账任务'},{to:'/carriers',label:'物流商配置'},{to:'/simulation',label:'故障模拟'}]
const route=useRoute(); const currentTitle=computed(()=>items.find(i=>i.to===route.path)?.label || 'TrackFlow')
</script>
