import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, type LoginPortal, type RegisterInput } from '@/api/auth'
import { ApiError } from '@/api/client'
import type { User } from '@/types/domain'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const initialized = ref(false)
  const loading = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)
  const canTeach = computed(() => user.value?.accountType === 'TEACHER')

  async function initialize(force = false): Promise<void> {
    if (initialized.value && !force) return
    loading.value = true
    try {
      await authApi.csrf()
      user.value = await authApi.me()
      initialized.value = true
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error
      user.value = null
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function login(identifier: string, password: string, portal: LoginPortal): Promise<void> {
    loading.value = true
    try {
      const result = await authApi.login(identifier, password, portal)
      user.value = result.user
      initialized.value = true
    } finally {
      loading.value = false
    }
  }

  async function register(input: RegisterInput): Promise<User> {
    loading.value = true
    try {
      return await authApi.register(input)
    } finally {
      loading.value = false
    }
  }

  async function claimStudentNo(identifier: string, password: string, studentNo: string): Promise<void> {
    loading.value = true
    try {
      await authApi.claimStudentNo(identifier, password, studentNo)
    } finally {
      loading.value = false
    }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      user.value = null
      initialized.value = true
    }
  }

  function clearSession(): void {
    user.value = null
    initialized.value = true
  }

  return { user, initialized, loading, isAuthenticated, isAdmin, canTeach, initialize, login, register, claimStudentNo, logout, clearSession }
})
