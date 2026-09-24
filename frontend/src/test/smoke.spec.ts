import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusBadge from '@/components/StatusBadge.vue'
import { routes } from '@/router'

describe('SEForge frontend smoke checks', () => {
  it('renders status values without raw enum separators', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'PENDING_CONFIRMATION' } })
    expect(wrapper.text()).toContain('PENDING CONFIRMATION')
    expect(wrapper.classes()).toContain('status-badge--pending-confirmation')
  })

  it('keeps three role workspaces lazy loaded and role gated', () => {
    const byPath = (path: string) => routes.find((route) => route.path === path)
    const admin = byPath('/admin')
    const teacher = byPath('/teacher')
    const student = byPath('/student')

    expect(admin?.meta?.workspace).toBe('admin')
    expect(teacher?.meta?.workspace).toBe('teacher')
    expect(student?.meta?.workspace).toBe('student')
    for (const workspace of [admin, teacher, student]) {
      expect(typeof workspace?.component).toBe('function')
      expect(workspace?.meta?.requiresAuth).toBe(true)
      expect(workspace?.children?.length).toBeGreaterThan(0)
      expect(workspace?.children?.every((route) => route.meta?.requiresAuth)).toBe(true)
    }

    for (const name of ['admin-home', 'admin-users', 'admin-audit']) {
      const route = admin?.children?.find((child) => child.name === name)
      expect(typeof route?.component, name).toBe('function')
      expect(route?.meta?.workspace).toBe('admin')
    }

    for (const prefix of ['teacher', 'student']) {
      const workspace = prefix === 'teacher' ? teacher : student
      const course = workspace?.children?.find((child) => child.name === `${prefix}-course`)
      expect(course?.path).toBe('courses/:courseId')
      for (const name of [`${prefix}-home`, `${prefix}-course`, `${prefix}-course-assistant`, `${prefix}-course-assignments`, `${prefix}-grades`]) {
        const route = workspace?.children?.find((child) => child.name === name)
        expect(typeof route?.component, name).toBe('function')
      }
      for (const name of [`${prefix}-course-reviews`, `${prefix}-course-dashboard`]) {
        const route = workspace?.children?.find((child) => child.name === name)
        expect(typeof route?.component, name).toBe('function')
        expect(route?.meta?.capability, name).toBe('course-staff')
      }
    }
  })

  it('exposes separate normal and admin login entries plus the student profile', () => {
    expect(routes.find((route) => route.path === '/login')?.name).toBe('login')
    expect(routes.find((route) => route.path === '/login/admin')?.name).toBe('admin-login')
    const student = routes.find((route) => route.path === '/student')
    expect(student?.children?.find((route) => route.name === 'student-profile')?.path).toBe('profile')
  })

  it('redirects legacy flat routes into the role workspaces', () => {
    for (const path of ['/', '/courses', '/courses/:courseId', '/courses/:courseId/assistant', '/courses/:courseId/assignments', '/reviews', '/dashboard', '/grades']) {
      const legacy = routes.find((route) => route.path === path)
      expect(legacy?.redirect, path).toBeTruthy()
    }
  })
})
