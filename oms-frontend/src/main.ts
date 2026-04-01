import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './assets/styles/mobile.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'

// 兼容历史二维码 hash 路由：/#/authorization/verify?token=xxx
const legacyHash = window.location.hash || ''
if (legacyHash.startsWith('#/authorization/verify')) {
  const normalized = legacyHash.slice(1)
  window.history.replaceState(null, '', normalized)
}

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})

app.mount('#app')
