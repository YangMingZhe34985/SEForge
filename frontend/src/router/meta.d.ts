import 'vue-router'

export {}

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth: boolean
    title: string
    capability?: 'teacher' | 'course-staff' | 'admin'
    workspace?: 'admin' | 'teacher' | 'student'
  }
}
