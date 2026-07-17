import { createRouter, createWebHistory } from 'vue-router'

const Dashboard = () => import('./views/Dashboard.vue')
const Shipments = () => import('./views/Shipments.vue')
const Events = () => import('./views/Events.vue')
const Anomalies = () => import('./views/Anomalies.vue')
const Reconciliation = () => import('./views/Reconciliation.vue')
const Carriers = () => import('./views/Carriers.vue')
const Simulation = () => import('./views/Simulation.vue')

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: Dashboard },
    { path: '/shipments', component: Shipments },
    { path: '/events', component: Events },
    { path: '/anomalies', component: Anomalies },
    { path: '/reconciliation', component: Reconciliation },
    { path: '/carriers', component: Carriers },
    { path: '/simulation', component: Simulation },
  ],
})
