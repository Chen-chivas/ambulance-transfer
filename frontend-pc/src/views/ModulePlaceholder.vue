<script setup lang="ts">
import { useRoute } from 'vue-router'
import { currentUser } from '../api/session'
import { homeFor } from '../router'
const route = useRoute()
</script>

<template>
  <section class="page-heading"><div><div class="eyebrow">{{ route.meta.role }}</div><h1>{{ route.meta.title }}</h1><p>{{ route.meta.description }}</p></div><el-tag type="info">待开发</el-tag></section>
  <el-card shadow="never"><el-empty description="页面入口已就绪，业务功能将在后续阶段接入。"><el-button type="primary" @click="$router.push(homeFor(currentUser?.role))">返回工作空间</el-button></el-empty></el-card>
</template>
