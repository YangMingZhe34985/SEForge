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

  it('keeps protected business pages lazy loaded and role gated', () => {
    const shell = routes.find((route) => route.path === '/')
    const children = shell?.children || []
    const dashboard = children.find((route) => route.name === 'dashboard')
    const admin = children.find((route) => route.name === 'admin-users')
    expect(typeof dashboard?.component).toBe('function')
    expect(dashboard?.meta?.capability).toBe('course-staff')
    expect(admin?.meta?.capability).toBe('admin')
    expect(children.every((route) => route.redirect || route.meta?.requiresAuth)).toBe(true)
  })
})
