import 'element-plus/dist/index.css'
import './assets/main.css'

import { createApp } from 'vue'
import ElementPlus from 'element-plus'
// 默认语言是英文，分页会显示 "20/page"、"Go to"。
// 这里统一换成中文，和页面其它文案保持一致。
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'

createApp(App).use(ElementPlus, { locale: zhCn }).use(router).mount('#app')
