<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Fold,
  Menu as MenuIcon,
  Position,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/courses'
import type { WorkspaceNavItem, WorkspaceSwitchLink } from './navigation'

const props = withDefaults(defineProps<{
  navigation: WorkspaceNavItem[]
  workspaceLabel: string
  showCourseSwitcher?: boolean
  switchLinks?: WorkspaceSwitchLink[]
}>(), {
  showCourseSwitcher: false,
  switchLinks: () => [],
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const courseStore = useCourseStore()
const collapsed = ref(false)

const roleLabel = computed(() => {
  if (auth.isAdmin) return '管理员'
  return auth.user?.accountType === 'TEACHER' ? '教师' : '学生'
})

async function handleLogout() {
  await auth.logout()
  courseStore.reset()
  await router.replace({ name: 'login' })
}

function selectCourse(courseId: string) {
  courseStore.select(courseId)
  if (route.params.courseId) {
    void router.push({ name: route.name as string, params: { ...route.params, courseId } })
  }
}

onMounted(async () => {
  if (!props.showCourseSwitcher || courseStore.courses.length) return
  try {
    await courseStore.load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程加载失败')
  }
})

watch(
  () => route.query.denied,
  (denied) => {
    if (denied) ElMessage.warning('当前账号无权访问该页面')
  },
  { immediate: true },
)
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--collapsed': collapsed }">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand__mark">SF</span>
        <div v-if="!collapsed"><strong>SEForge</strong><small>{{ workspaceLabel }}</small></div>
      </div>

      <nav class="sidebar__nav" aria-label="主导航">
        <template v-for="item in navigation" :key="item.label">
          <router-link
            v-if="item.visible !== false"
            :to="item.to"
            class="nav-item"
            :class="{ 'nav-item--disabled': item.enabled === false }"
            :title="collapsed ? item.label : undefined"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="!collapsed">{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <button class="sidebar__toggle" type="button" @click="collapsed = !collapsed">
        <el-icon><component :is="collapsed ? MenuIcon : Fold" /></el-icon>
        <span v-if="!collapsed">收起导航</span>
      </button>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div v-if="showCourseSwitcher" class="course-switcher">
          <span>当前课程</span>
          <el-select
            :model-value="courseStore.selectedCourseId"
            placeholder="请先选择课程"
            filterable
            :loading="courseStore.loading"
            @update:model-value="selectCourse"
          >
            <el-option v-for="course in courseStore.courses" :key="course.id" :label="`${course.code} · ${course.name}`" :value="course.id" />
          </el-select>
        </div>
        <div v-else />
        <el-dropdown trigger="click">
          <button type="button" class="user-menu">
            <span class="avatar"><el-icon><User /></el-icon></span>
            <span><strong>{{ auth.user?.displayName }}</strong><small>{{ roleLabel }}</small></span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="link in switchLinks" :key="link.label" :icon="Position" @click="router.push(link.to)">{{ link.label }}</el-dropdown-item>
              <el-dropdown-item :icon="SwitchButton" divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>
      <main class="page-content"><router-view /></main>
    </section>
  </div>
</template>
