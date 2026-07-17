export const statusText: Record<string, string> = {
  CREATED: '已创建',
  PICKED_UP: '已揽收',
  IN_TRANSIT: '运输中',
  ARRIVED_AT_STATION: '到达站点',
  OUT_FOR_DELIVERY: '派送中',
  DELIVERY_FAILED: '派送失败',
  DELIVERED: '已签收',
  RETURNING: '退回中',
  RETURNED: '已退回',
  CANCELLED: '已取消',
  UNKNOWN: '未知状态',
}

export const statusType = (status: string) => {
  if (status === 'DELIVERED') return 'success'
  if (status?.includes('FAILED') || status === 'UNKNOWN') return 'danger'
  if (status === 'RETURNING' || status === 'RETURNED') return 'warning'
  return 'info'
}

export const formatTime = (value?: string) => (value ? new Date(value).toLocaleString() : '-')
