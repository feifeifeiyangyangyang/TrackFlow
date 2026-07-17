import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from './views/Dashboard.vue'
import Shipments from './views/Shipments.vue'
import Events from './views/Events.vue'
import Anomalies from './views/Anomalies.vue'
import Reconciliation from './views/Reconciliation.vue'
import Carriers from './views/Carriers.vue'
import Simulation from './views/Simulation.vue'
export default createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/dashboard' }, { path: '/dashboard', component: Dashboard }, { path: '/shipments', component: Shipments },
  { path: '/events', component: Events }, { path: '/anomalies', component: Anomalies }, { path: '/reconciliation', component: Reconciliation },
  { path: '/carriers', component: Carriers }, { path: '/simulation', component: Simulation }
]})
