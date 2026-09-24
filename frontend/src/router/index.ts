import { createRouter, createWebHistory, type LocationQuery, type RouteLocationRaw, type RouteParams, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/courses'

function roleHomeName(): 'admin-home' | 'teacher-home' | 'student-home' {
  const auth = useAuthStore(pinia)
  if (auth.isAdmin) return 'admin-home'
  if (auth.canTeach) return 'teacher-home'
  return 'student-home'
}

function roleHome(): RouteLocationRaw {
  return { name: roleHomeName() }
}

function deniedHome(): RouteLocationRaw {
  return { name: roleHomeName(), query: { denied: '1' } }
}

function unavailableHome(): RouteLocationRaw {
  return { name: roleHomeName(), query: { unavailable: '1' } }
}

function workspacePrefix(): 'teacher' | 'student' {
  // Admins act as teachers inside course workspaces; the admin workspace stays separate.
  return useAuthStore(pinia).canTeach ? 'teacher' : 'student'
}

function legacyCourseRoute(
  to: { params: RouteParams; query: LocationQuery },
  suffix: '' | '-assistant' | '-assignments',
): RouteLocationRaw {
  return {
    name: `${workspacePrefix()}-course${suffix}`,
    params: { courseId: String(to.params.courseId ?? '') },
    query: to.query,
  }
}

function legacyStaffRoute(section: 'reviews' | 'dashboard'): RouteLocationRaw {
  const courseId = useCourseStore(pinia).selectedCourseId
  if (!courseId) return roleHome()
  return { name: `${workspacePrefix()}-course-${section}`, params: { courseId } }
}

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' },
  },
  { path: '/', redirect: () => roleHome() },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, title: 'SEForge 管理控制台', workspace: 'admin' },
    children: [
      { path: '', name: 'admin-home', component: () => import('@/views/AdminHomeView.vue'), meta: { requiresAuth: true, title: '管理概览', workspace: 'admin' } },
      { path: 'users', name: 'admin-users', component: () => import('@/views/AdminUsersView.vue'), meta: { requiresAuth: true, title: '用户与学期', workspace: 'admin' } },
      { path: 'audit', name: 'admin-audit', component: () => import('@/views/AdminAuditView.vue'), meta: { requiresAuth: true, title: '审计日志', workspace: 'admin' } },
    ],
  },
  {
    path: '/teacher',
    component: () => import('@/layouts/TeacherLayout.vue'),
    meta: { requiresAuth: true, title: 'SEForge 教学工作台', workspace: 'teacher' },
    children: [
      { path: '', name: 'teacher-home', component: () => import('@/views/TeacherHomeView.vue'), meta: { requiresAuth: true, title: '教学工作台', workspace: 'teacher' } },
      { path: 'courses/:courseId', name: 'teacher-course', component: () => import('@/views/CourseWorkspaceView.vue'), meta: { requiresAuth: true, title: '课程内容', workspace: 'teacher' } },
      { path: 'courses/:courseId/assistant', name: 'teacher-course-assistant', component: () => import('@/views/CourseAssistantView.vue'), meta: { requiresAuth: true, title: '课程助手', workspace: 'teacher' } },
      { path: 'courses/:courseId/assignments', name: 'teacher-course-assignments', component: () => import('@/views/AssignmentsView.vue'), meta: { requiresAuth: true, title: '作业与 Tutor', workspace: 'teacher' } },
      { path: 'courses/:courseId/reviews', name: 'teacher-course-reviews', component: () => import('@/views/ReviewsView.vue'), meta: { requiresAuth: true, title: '智能评审', workspace: 'teacher', capability: 'course-staff' } },
      { path: 'courses/:courseId/dashboard', name: 'teacher-course-dashboard', component: () => import('@/views/DashboardView.vue'), meta: { requiresAuth: true, title: '教学 Dashboard', workspace: 'teacher', capability: 'course-staff' } },
      { path: 'grades', name: 'teacher-grades', component: () => import('@/views/GradesView.vue'), meta: { requiresAuth: true, title: '成绩与反馈', workspace: 'teacher' } },
    ],
  },
  {
    path: '/student',
    component: () => import('@/layouts/StudentLayout.vue'),
    meta: { requiresAuth: true, title: 'SEForge 学习工作台', workspace: 'student' },
    children: [
      { path: '', name: 'student-home', component: () => import('@/views/StudentHomeView.vue'), meta: { requiresAuth: true, title: '我的课程', workspace: 'student' } },
      { path: 'courses/:courseId', name: 'student-course', component: () => import('@/views/CourseWorkspaceView.vue'), meta: { requiresAuth: true, title: '课程内容', workspace: 'student' } },
      { path: 'courses/:courseId/assistant', name: 'student-course-assistant', component: () => import('@/views/CourseAssistantView.vue'), meta: { requiresAuth: true, title: '课程助手', workspace: 'student' } },
      { path: 'courses/:courseId/assignments', name: 'student-course-assignments', component: () => import('@/views/AssignmentsView.vue'), meta: { requiresAuth: true, title: '作业与 Tutor', workspace: 'student' } },
      { path: 'courses/:courseId/reviews', name: 'student-course-reviews', component: () => import('@/views/ReviewsView.vue'), meta: { requiresAuth: true, title: '智能评审', workspace: 'student', capability: 'course-staff' } },
      { path: 'courses/:courseId/dashboard', name: 'student-course-dashboard', component: () => import('@/views/DashboardView.vue'), meta: { requiresAuth: true, title: '教学 Dashboard', workspace: 'student', capability: 'course-staff' } },
      { path: 'grades', name: 'student-grades', component: () => import('@/views/GradesView.vue'), meta: { requiresAuth: true, title: '成绩与反馈', workspace: 'student' } },
    ],
  },

  // Legacy flat URLs (pre-workspace information architecture) keep working via redirects.
  { path: '/courses', redirect: () => roleHome() },
  { path: '/courses/:courseId', redirect: (to) => legacyCourseRoute(to, '') },
  { path: '/courses/:courseId/assistant', redirect: (to) => legacyCourseRoute(to, '-assistant') },
  { path: '/courses/:courseId/assignments', redirect: (to) => legacyCourseRoute(to, '-assignments') },
  { path: '/reviews', redirect: () => legacyStaffRoute('reviews') },
  { path: '/dashboard', redirect: () => legacyStaffRoute('dashboard') },
  {
    path: '/grades',
    redirect: () => ({ name: useAuthStore(pinia).canTeach ? 'teacher-grades' : 'student-grades' }),
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
  if (to.name === 'login' && auth.isAuthenticated) return roleHome()

  // Workspace separation. The server remains the authority for every API call; these
  // guards only keep each identity inside its own navigation surface.
  if (auth.isAuthenticated) {
    if (to.meta.workspace === 'admin' && !auth.isAdmin) return deniedHome()
    if (to.meta.workspace === 'teacher' && !auth.canTeach) return deniedHome()
    if (to.meta.workspace === 'student' && auth.canTeach) {
      // Teachers/admins arriving via legacy links or bookmarks land on the teacher
      // counterpart of the same student route (names are symmetric by design).
      return { name: String(to.name).replace(/^student-/, 'teacher-'), params: to.params, query: to.query }
    }
  }

  if (to.params.courseId) {
    const courses = useCourseStore(pinia)
    try {
      if (!courses.courses.length) await courses.load()
    } catch {
      return unavailableHome()
    }
    const requestedCourseId = Array.isArray(to.params.courseId)
      ? to.params.courseId[0]
      : String(to.params.courseId)
    if (!requestedCourseId || !courses.courses.some((course) => course.id === requestedCourseId)) {
      return deniedHome()
    }
    courses.select(requestedCourseId)
  }

  if (to.meta.capability === 'teacher' && !auth.canTeach) return deniedHome()
  if (to.meta.capability === 'course-staff') {
    const courses = useCourseStore(pinia)
    try {
      if (!courses.courses.length) await courses.load()
    } catch {
      return unavailableHome()
    }
    if (!auth.isAdmin && !courses.canManageSelected) return deniedHome()
  }
  if (to.meta.capability === 'admin' && !auth.isAdmin) return deniedHome()
  document.title = `${to.meta.title} · SEForge`
  return true
})

export default router
