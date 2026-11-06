<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { auditLabels, auditTypes, type Company } from '../../api/business'
import { request, messageOf } from '../../api/http'
import VehicleManager from '../../components/VehicleManager.vue'
const company = ref<Company | null>(null)
const error = ref('')
async function load() { error.value = ''; try { company.value = await request<Company>('/company/profile') } catch (e) { error.value = messageOf(e) } }
onMounted(load)
</script>
<template>
  <section class="page-heading"><div><div class="eyebrow">FLEET MANAGEMENT</div><h1>{{ company?.name || '车辆管理' }}</h1><p>维护本车行的车辆资料、服务等级与可用状态。</p></div><el-tag v-if="company" :type="auditTypes[company.auditStatus]">{{ auditLabels[company.auditStatus] }}</el-tag></section>
  <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
  <el-alert v-if="company && company.auditStatus !== 'APPROVED'" :title="company.auditRemark || '车行资质尚未通过或已过期，可继续维护车辆资料。'" type="warning" :closable="false" show-icon class="form-alert" />
  <el-card shadow="never" class="business-card"><VehicleManager /></el-card>
</template>
