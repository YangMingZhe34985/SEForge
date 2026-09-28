<script setup lang="ts">
import { Checked, DataAnalysis, UserFilled } from '@element-plus/icons-vue'
withDefaults(defineProps<{ title: string; description: string; kind?: 'overview' | 'users' | 'audit' }>(), { kind: 'overview' })
</script>

<template>
  <header class="admin-hero">
    <div class="admin-hero__copy">
      <p class="eyebrow">SEForge Workspace</p>
      <h1>{{ title }}</h1>
      <p class="admin-hero__description">{{ description }}</p>
    </div>
    <div class="admin-hero__art" aria-hidden="true">
      <div class="admin-hero__orbit" />
      <div class="admin-hero__sheet"><i /><i /><i /><el-icon><component :is="kind === 'users' ? UserFilled : DataAnalysis" /></el-icon></div>
      <span class="admin-hero__seal"><el-icon><Checked /></el-icon></span>
    </div>
    <div class="admin-hero__actions"><slot /></div>
  </header>
</template>

<style scoped>
.admin-hero { position: relative; isolation: isolate; display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 24px; min-height: 182px; padding: 20px 0 28px; }
.admin-hero__copy { z-index: 1; max-width: 760px; }
h1 { margin: 10px 0; font-size: clamp(30px, 3.3vw, 44px); line-height: 1.25; letter-spacing: -.04em; color: var(--ink); }
.eyebrow { color: var(--brand); font-size: 12px; }
.admin-hero__description { color: var(--muted); line-height: 1.8; margin: 0; max-width: 640px; }
.admin-hero__actions { z-index: 1; display: flex; gap: 12px; }
.admin-hero__art { position: absolute; z-index: -1; width: 240px; height: 160px; right: 14%; top: -5px; opacity: .65; pointer-events: none; }
.admin-hero__orbit { position: absolute; inset: 0 -35px; border-radius: 50%; background: radial-gradient(ellipse, #b9d3ff80, #e8f2ff00 70%); transform: rotate(-20deg); }
.admin-hero__sheet { position: absolute; width: 140px; height: 125px; left: 28px; top: 15px; border: 2px solid white; border-radius: 16px; background: linear-gradient(145deg, white, #dceaff); box-shadow: 12px 15px 25px #5790ef22; transform: rotate(-9deg); padding: 22px; }
.admin-hero__sheet i { display: block; height: 8px; border-radius: 8px; background: linear-gradient(90deg, #85afff, #d2e2ff); margin-bottom: 12px; }
.admin-hero__sheet i:nth-child(2) { width: 70%; }
.admin-hero__sheet .el-icon { font-size: 35px; color: #6f9cff; }
.admin-hero__seal { position: absolute; right: 16px; bottom: 3px; display: grid; place-items: center; width: 75px; height: 75px; border: 5px solid #fff; border-radius: 22px; background: linear-gradient(135deg, #acd5ff, #3675f2); color: white; font-size: 43px; box-shadow: 0 12px 20px #4780e025; transform: rotate(8deg); }
@media (max-width: 1200px) { .admin-hero__art { right: 0; opacity: .25; } }
@media (max-width: 600px) { .admin-hero { min-height: auto; padding-top: 8px; gap: 20px; } .admin-hero__art { display: none; } .admin-hero__actions { width: 100%; flex-wrap: wrap; } }
</style>
