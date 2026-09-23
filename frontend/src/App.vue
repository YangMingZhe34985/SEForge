<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const handleUnauthorized = () => {
  auth.clearSession()
  void router.replace({ name: 'login', query: { expired: '1' } })
}

onMounted(() => window.addEventListener('seforge:unauthorized', handleUnauthorized))
onBeforeUnmount(() => window.removeEventListener('seforge:unauthorized', handleUnauthorized))
</script>

<template>
  <el-config-provider size="default">
    <router-view />
  </el-config-provider>
</template>
