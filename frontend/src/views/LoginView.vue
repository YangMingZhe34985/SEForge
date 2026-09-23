<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()
const loginForm = reactive({ identifier: '', password: '' })
const registerForm = reactive({ email: '', username: '', displayName: '', password: '', confirmPassword: '' })

const loginRules: FormRules = {
  identifier: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名应为 3–32 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]+$/, message: '用户名仅可包含字母、数字、点、下划线和连字符', trigger: 'blur' },
  ],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 10, max: 72, message: '密码应为 10–72 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== registerForm.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submitLogin() {
  if (!(await loginFormRef.value?.validate().catch(() => false))) return
  try {
    await auth.login(loginForm.identifier.trim(), loginForm.password)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : '/courses'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  }
}

async function submitRegister() {
  if (!(await registerFormRef.value?.validate().catch(() => false))) return
  try {
    await auth.register({
      email: registerForm.email.trim(),
      username: registerForm.username.trim(),
      displayName: registerForm.displayName.trim(),
      password: registerForm.password,
    })
    ElMessage.success('注册成功，请使用新账号登录')
    loginForm.identifier = registerForm.username
    mode.value = 'login'
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败')
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <div class="auth-brand"><span>SF</span> SEForge</div>
      <div>
        <p class="auth-kicker">AI-NATIVE SOFTWARE ENGINEERING EDUCATION</p>
        <h1>把课程知识、作业实践与智能反馈连接起来。</h1>
        <p>面向高校软件工程课程的学习与实践平台。每一次问答、辅导与评审，都绑定真实课程上下文。</p>
      </div>
      <div class="auth-principles">
        <span>Course-scoped RAG</span><span>Guided Tutor</span><span>Teacher-in-the-loop</span>
      </div>
    </section>

    <section class="auth-form-wrap">
      <div class="auth-card">
        <div class="auth-tabs">
          <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
          <button type="button" :class="{ active: mode === 'register' }" @click="mode = 'register'">学生注册</button>
        </div>

        <div v-if="route.query.expired" class="auth-notice">登录状态已失效，请重新登录。</div>
        <div v-if="route.query.unavailable" class="auth-notice">服务暂时不可用，请稍后重试。</div>

        <el-form v-if="mode === 'login'" ref="loginFormRef" :model="loginForm" :rules="loginRules" label-position="top" @submit.prevent="submitLogin">
          <el-form-item label="用户名或邮箱" prop="identifier"><el-input v-model="loginForm.identifier" autocomplete="username" /></el-form-item>
          <el-form-item label="密码" prop="password"><el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" /></el-form-item>
          <el-button native-type="submit" type="primary" size="large" class="auth-submit" :loading="auth.loading">进入工作台</el-button>
        </el-form>

        <el-form v-else ref="registerFormRef" :model="registerForm" :rules="registerRules" label-position="top" @submit.prevent="submitRegister">
          <div class="form-pair">
            <el-form-item label="姓名" prop="displayName"><el-input v-model="registerForm.displayName" autocomplete="name" /></el-form-item>
            <el-form-item label="用户名" prop="username"><el-input v-model="registerForm.username" autocomplete="username" /></el-form-item>
          </div>
          <el-form-item label="邮箱" prop="email"><el-input v-model="registerForm.email" autocomplete="email" /></el-form-item>
          <div class="form-pair">
            <el-form-item label="密码" prop="password"><el-input v-model="registerForm.password" type="password" show-password autocomplete="new-password" /></el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="registerForm.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
          </div>
          <el-button native-type="submit" type="primary" size="large" class="auth-submit" :loading="auth.loading">创建学生账号</el-button>
          <p class="auth-help">教师与管理员账号由管理员创建。</p>
        </el-form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; grid-template-columns: minmax(360px, 1.15fr) minmax(430px, .85fr); background: #f7f8fb; }
.auth-intro { position: relative; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; padding: clamp(36px, 6vw, 80px); background: #111a30; color: #fff; }
.auth-intro::after { content: ''; position: absolute; width: 440px; height: 440px; right: -180px; bottom: -190px; border: 90px solid rgba(64,213,184,.1); border-radius: 50%; }
.auth-brand { display: flex; align-items: center; gap: 11px; font-size: 19px; font-weight: 750; }
.auth-brand span { width: 39px; height: 39px; display: grid; place-items: center; border-radius: 10px; background: linear-gradient(140deg,#6681ff,#2ad0b1); font-size: 13px; }
.auth-kicker { margin: 0 0 16px; color: #58d9c1; font-size: 11px; font-weight: 750; letter-spacing: .13em; }
.auth-intro h1 { max-width: 720px; margin: 0; font-size: clamp(34px, 5vw, 64px); line-height: 1.1; letter-spacing: -.045em; }
.auth-intro h1 + p { max-width: 640px; color: #abb6cf; font-size: 16px; line-height: 1.8; }
.auth-principles { display: flex; flex-wrap: wrap; gap: 9px; }
.auth-principles span { border: 1px solid #34415e; border-radius: 999px; padding: 7px 11px; color: #aeb9d0; font-size: 11px; }
.auth-form-wrap { display: grid; place-items: center; padding: 30px; }
.auth-card { width: min(100%, 510px); border: 1px solid #e1e5ed; border-radius: 17px; padding: clamp(24px, 4vw, 42px); background: #fff; box-shadow: 0 20px 60px rgba(24,39,72,.09); }
.auth-tabs { display: grid; grid-template-columns: 1fr 1fr; margin-bottom: 30px; border-bottom: 1px solid #e4e7ed; }
.auth-tabs button { border: 0; border-bottom: 2px solid transparent; padding: 13px; background: transparent; color: #7a8497; }
.auth-tabs button.active { border-color: #2847d7; color: #2847d7; font-weight: 700; }
.auth-submit { width: 100%; margin-top: 8px; }
.auth-notice { margin-bottom: 18px; border-radius: 8px; padding: 10px 12px; background: #fff4dc; color: #93620e; font-size: 13px; }
.form-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.auth-help { margin: 16px 0 0; color: #7b8495; font-size: 12px; text-align: center; }
@media (max-width: 860px) { .auth-page { grid-template-columns: 1fr; } .auth-intro { min-height: 310px; gap: 40px; } .auth-intro h1 { font-size: 36px; } }
@media (max-width: 520px) { .auth-form-wrap { padding: 16px; } .form-pair { grid-template-columns: 1fr; gap: 0; } }
</style>
