<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const handleUnauthorized = () => {
  const current = router.currentRoute.value
  if (!auth.isAuthenticated && !current.meta.requiresAuth) return
  const loginRoute = current.meta.workspace === 'admin' || current.name === 'admin-login' ? 'admin-login' : 'login'
  auth.clearSession()
  void router.replace({ name: loginRoute, query: { expired: '1' } })
}

onMounted(() => window.addEventListener('seforge:unauthorized', handleUnauthorized))
onBeforeUnmount(() => window.removeEventListener('seforge:unauthorized', handleUnauthorized))
</script>

<template>
  <el-config-provider size="default">
    <router-view />
  </el-config-provider>
</template>
