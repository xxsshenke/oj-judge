import { createRouter, createWebHashHistory } from 'vue-router'
import ProblemView from '../views/ProblemView.vue'
import SubmissionsView from '../views/SubmissionsView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/problems' },
    { path: '/problems', name: 'problem', component: ProblemView },
    { path: '/submissions', name: 'submissions', component: SubmissionsView },
  ],
})

export default router
