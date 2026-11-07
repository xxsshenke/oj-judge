import { createRouter, createWebHashHistory } from 'vue-router'
import ProblemSetView from '../views/ProblemSetView.vue'
import ProblemView from '../views/ProblemView.vue'
import SolutionView from '../views/SolutionView.vue'
import SubmissionsView from '../views/SubmissionsView.vue'
import UserView from '../views/UserView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/problemset' },
    { path: '/problemset', name: 'problemset', component: ProblemSetView },
    { path: '/problems', name: 'problem', component: ProblemView },
    // 题解页用 query 传参（?problemId=1&id=2），与现有 /problems?problemId=1 的风格一致，
    // 不引入动态段路由 —— 现有路由全是扁平的，加 /solution/:id 会打破这个一致
    { path: '/solution', name: 'solution', component: SolutionView },
    { path: '/submissions', name: 'submissions', component: SubmissionsView },
    { path: '/user', name: 'user', component: UserView },
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

export default router
