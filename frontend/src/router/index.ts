import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/courses'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppShell.vue'),
    meta: { requiresAuth: true, title: 'SEForge' },
    children: [
      { path: '', redirect: '/courses' },
      { path: 'courses', name: 'courses', component: () => import('@/views/CoursesView.vue'), meta: { requiresAuth: true, title: '我的课程' } },
      { path: 'courses/:courseId/assistant', name: 'assistant', component: () => import('@/views/CourseAssistantView.vue'), meta: { requiresAuth: true, title: '课程助手' } },
      { path: 'courses/:courseId/assignments', name: 'assignments', component: () => import('@/views/AssignmentsView.vue'), meta: { requiresAuth: true, title: '作业与 Tutor' } },
      { path: 'reviews', name: 'reviews', component: () => import('@/views/ReviewsView.vue'), meta: { requiresAuth: true, title: '智能评审', capability: 'course-staff' } },
      { path: 'grades', name: 'grades', component: () => import('@/views/GradesView.vue'), meta: { requiresAuth: true, title: '成绩与反馈' } },
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { requiresAuth: true, title: '教学 Dashboard', capability: 'course-staff' } },
      { path: 'admin/users', name: 'admin-users', component: () => import('@/views/AdminUsersView.vue'), meta: { requiresAuth: true, title: '用户管理', capability: 'admin' } },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { requiresAuth: false, title: '页面不存在' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  try {
    await auth.initialize()
  } catch {
    if (to.meta.requiresAuth) return { name: 'login', query: { redirect: to.fullPath, unavailable: '1' } }
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) return { name: 'courses' }

  if (to.params.courseId) {
    const courses = useCourseStore(pinia)
    try {
      if (!courses.courses.length) await courses.load()
    } catch {
      return { name: 'courses', query: { unavailable: '1' } }
    }
    const requestedCourseId = Array.isArray(to.params.courseId)
      ? to.params.courseId[0]
      : String(to.params.courseId)
    if (!requestedCourseId || !courses.courses.some((course) => course.id === requestedCourseId)) {
      return { name: 'courses', query: { denied: '1' } }
    }
    courses.select(requestedCourseId)
  }

  if (to.meta.capability === 'teacher' && !auth.canTeach) return { name: 'courses', query: { denied: '1' } }
  if (to.meta.capability === 'course-staff') {
    const courses = useCourseStore(pinia)
    try {
      if (!courses.courses.length) await courses.load()
    } catch {
      return { name: 'courses', query: { unavailable: '1' } }
    }
    if (!auth.isAdmin && !courses.canManageSelected) return { name: 'courses', query: { denied: '1' } }
  }
  if (to.meta.capability === 'admin' && !auth.isAdmin) return { name: 'courses', query: { denied: '1' } }
  document.title = `${to.meta.title} · SEForge`
  return true
})

export default router
