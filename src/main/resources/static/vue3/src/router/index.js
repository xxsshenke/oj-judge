import { createRouter, createWebHashHistory } from 'vue-router'
import ProblemSetView from '../views/ProblemSetView.vue'
import ProblemView from '../views/ProblemView.vue'
import SubmissionsView from '../views/SubmissionsView.vue'
import UserView from '../views/UserView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/problemset' },
    { path: '/problemset', name: 'problemset', component: ProblemSetView },
    { path: '/problems', name: 'problem', component: ProblemView },
    { path: '/submissions', name: 'submissions', component: SubmissionsView },
    { path: '/user', name: 'user', component: UserView },
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

export default router
