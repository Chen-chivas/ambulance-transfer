<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useSystemStore } from '../../stores/system'
import { request, messageOf } from '../../api/http'
import type { Overview } from '../../api/business'

const system = useSystemStore()
const stats = ref<Overview | null>(null)
const statsError = ref('')
const loading = ref(false)
async function refresh() {
  if (loading.value) return
  loading.value = true; statsError.value = ''
  await Promise.allSettled([system.refresh(), (async () => {
    try { stats.value = await request<Overview>('/admin/overview') }
    catch (e) { stats.value = null; statsError.value = messageOf(e) }
  })()])
  loading.value = false
}
onMounted(refresh)
const stages = [
  { title: '环境与骨架', content: '前后端工程、页面导航、连接检查及数据库结构。', current: true },
  { title: '基础资料与权限', content: '登录与角色隔离、车行审核、车辆维护与调度账号。', current: true },
  { title: '下单与报价', content: '模拟下单、资质过滤、订单池、车行报价与用户选标。', current: true },
  { title: '调度与履约', content: '派车、司机节点、订单时间轴与取消流程。', current: false },
]
</script>

<template>
  <section class="page-heading">
    <div><div class="eyebrow">DISPATCH WORKSPACE</div><h1>系统概览</h1><p>从一次转运需求，到每一个服务节点。</p></div>
    <el-button :loading="loading" @click="refresh">刷新数据</el-button>
  </section>
  <section class="intro-panel">
    <div><el-tag effect="dark">基础资料与权限已接入</el-tag><h2>每一程，从可靠的准备开始</h2><p>录入车行、审核资质、维护车辆，为转运调度做好准备。</p><RouterLink to="/admin/companies"><el-button type="primary" class="intro-action">进入车行管理</el-button></RouterLink></div>
    <div class="intro-symbol" aria-hidden="true">+</div>
  </section>
  <el-alert v-if="statsError" :title="statsError" type="error" :closable="false" show-icon class="form-alert" />
  <section class="stats-grid" aria-label="业务统计">
    <el-card shadow="never"><p>入驻车行</p><strong>{{ stats?.companies ?? '—' }}</strong><small>已通过 {{ stats?.approved ?? '—' }} 家</small></el-card>
    <el-card shadow="never"><p>待审核车行</p><strong>{{ stats?.pending ?? '—' }}</strong><RouterLink to="/admin/companies?status=PENDING">前往审核 →</RouterLink></el-card>
    <el-card shadow="never"><p>登记车辆</p><strong>{{ stats?.vehicles ?? '—' }}</strong><small>全部车行车辆总数</small></el-card>
    <el-card shadow="never"><p>空闲车辆</p><strong>{{ stats?.idleVehicles ?? '—' }}</strong><small>车辆状态为空闲</small></el-card>
  </section>
  <section class="connection-grid" aria-label="连接状态">
    <el-card shadow="never">
      <div class="card-top"><span>后端服务</span><el-tag :type="system.backend === 'ok' ? 'success' : 'info'">{{ system.backend === 'ok' ? '已连接' : system.backend === 'error' ? '未连接' : '待检查' }}</el-tag></div>
      <h3>Spring Boot</h3><p>{{ system.backendMessage }}</p>
    </el-card>
    <el-card shadow="never">
      <div class="card-top"><span>业务数据库</span><el-tag :type="system.database === 'ok' ? 'success' : 'info'">{{ system.database === 'ok' ? '已连接' : system.database === 'error' ? '未连接' : '待检查' }}</el-tag></div>
      <h3>SQL Server</h3><p>{{ system.databaseMessage }}</p>
    </el-card>
  </section>
  <div class="section-heading"><h2>建设路线</h2><span v-if="system.checkedAt">最近检查 {{ system.checkedAt }}</span></div>
  <section class="roadmap">
    <article v-for="(stage, index) in stages" :key="stage.title" :class="{ current: stage.current }">
      <div class="stage-number">{{ String(index + 1).padStart(2, '0') }}</div>
      <div><h3>{{ stage.title }}</h3><p>{{ stage.content }}</p></div>
      <el-tag :type="stage.current ? 'success' : 'info'" effect="plain">{{ stage.current ? '已完成' : '待开发' }}</el-tag>
    </article>
  </section>
  <el-alert title="已支持下单、报价与选标；派车、司机履约和取消将在下一阶段接入。" type="info" :closable="false" show-icon />
</template>
