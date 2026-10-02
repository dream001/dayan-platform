import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { i18n } from './i18n'
import { installPermissionDirective } from './directives/permission'
import { setUnauthorizedHandler } from './services/http'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(i18n)
app.use(ElementPlus)
installPermissionDirective(app)
setUnauthorizedHandler(() => {
  useAuthStore(pinia).reset()
  void router.replace({ name: 'unauthorized' })
})
app.use(router)
app.mount('#app')
