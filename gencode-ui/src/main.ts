import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import { perm } from './directives/perm'
import 'ant-design-vue/dist/reset.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 全量注册 Ant Design Vue 组件（一期从简，不做按需引入）
app.use(Antd)
app.directive('perm', perm)

app.mount('#app')
