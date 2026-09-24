import type { Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'

export interface WorkspaceNavItem {
  label: string
  icon: Component
  to: RouteLocationRaw
  visible?: boolean
  enabled?: boolean
}

export interface WorkspaceSwitchLink {
  label: string
  to: RouteLocationRaw
}
