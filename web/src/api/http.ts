import axios from 'axios'
export const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 10000, headers: { 'X-Operator-Name': 'demo-operator' } })
http.interceptors.response.use(r => r.data, e => Promise.reject(new Error(e.response?.data?.message || e.message || '请求失败')))
