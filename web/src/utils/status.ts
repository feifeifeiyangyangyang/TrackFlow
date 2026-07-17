export const statusText: Record<string, string> = { CREATED:'已创建', PICKED_UP:'已揽收', IN_TRANSIT:'运输中', ARRIVED_AT_STATION:'到达站点', OUT_FOR_DELIVERY:'派送中', DELIVERY_FAILED:'派送失败', DELIVERED:'已签收', RETURNING:'退回中', RETURNED:'已退回', CANCELLED:'已取消', UNKNOWN:'未知' }
export const statusType = (s: string) => s === 'DELIVERED' ? 'success' : s?.includes('FAILED') || s === 'UNKNOWN' ? 'danger' : s === 'RETURNING' ? 'warning' : 'info'
export const formatTime = (value?: string) => value ? new Date(value).toLocaleString() : '-'
