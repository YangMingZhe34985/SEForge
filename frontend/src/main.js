import { createApp } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus';
import App from './App.vue'
import router from './router'
import './assets/css/global.css'
import 'font-awesome/css/font-awesome.min.css'
import '@fortawesome/fontawesome-free/css/all.min.css';
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'highlight.js/styles/github.css';
import { loadScript } from './utils/loadScript';



const app = createApp(App)
app.config.globalProperties.$axios = axios
// 全局注册消息组件
app.config.globalProperties.$message = ElMessage;

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.use(ElementPlus)
app.mount('#app')
