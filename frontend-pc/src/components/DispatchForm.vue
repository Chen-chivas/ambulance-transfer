<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { request, messageOf } from '../api/http'
import type { Vehicle } from '../api/business'
import type { TransferOrder } from '../api/orders'

const props = defineProps<{ order: TransferOrder }>()
const emit = defineEmits<{ changed: []; busy: [value: boolean] }>()
interface ResourcePerson { id: number; name: string; certExpireAt: string }
interface Resources { vehicles: Vehicle[]; drivers: ResourcePerson[]; nurses: ResourcePerson[] }
const resources = ref<Resources>({ vehicles: [], drivers: [], nurses: [] })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formRef = ref<FormInstance>()
const form = reactive({ vehicleId: undefined as number | undefined, driverId: undefined as number | undefined, nurseId: undefined as number | undefined })
const rules = computed<FormRules>(() => ({ vehicleId: [{ required: true, message: '请选择空闲车辆', trigger: 'change' }], driverId: [{ required: true, message: '请选择司机', trigger: 'change' }], nurseId: [{ required: props.order.serviceLevel === 'MONITORING', message: '监护型转运必须配备护士', trigger: 'change' }] }))
let sequence = 0
async function load() {
  const seq = ++sequence; loading.value = true; error.value = ''
  try { const value = await request<Resources>(`/company/orders/${props.order.id}/resources`); if (seq === sequence) resources.value = value }
  catch (e) { if (seq === sequence) error.value = messageOf(e) }
  finally { if (seq === sequence) loading.value = false }
}
async function dispatch() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true; emit('busy', true)
  try {
    await request(`/company/orders/${props.order.id}/dispatch`, 'POST', { ...form, nurseId: form.nurseId ?? null })
    ElMessage.success('派车成功，司机可在任务列表查看'); emit('changed')
  } catch (e) {
    ElMessage.error(messageOf(e)); Object.assign(form, { vehicleId: undefined, driverId: undefined, nurseId: undefined }); await load(); emit('changed')
  } finally { saving.value = false; emit('busy', false) }
}
onMounted(load)
onUnmounted(() => { sequence++ })
</script>

<template>
  <section class="quote-card" v-loading="loading">
    <div class="section-heading"><h2>分配转运资源</h2><el-button :disabled="saving" @click="load">刷新可用资源</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <p>仅显示本车行符合等级、证书有效且空闲的资源。{{ order.serviceLevel === 'MONITORING' ? '监护型必须配备护士。' : '基础型可选配护士。' }}</p>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving || loading || !!error">
      <el-form-item label="派车车辆" prop="vehicleId"><el-select v-model="form.vehicleId" aria-label="派车车辆" placeholder="选择空闲车辆" style="width:100%"><el-option v-for="v in resources.vehicles" :key="v.id" :value="v.id" :label="v.plateNo" /></el-select></el-form-item>
      <el-form-item label="任务司机" prop="driverId"><el-select v-model="form.driverId" aria-label="任务司机" placeholder="选择司机" style="width:100%"><el-option v-for="p in resources.drivers" :key="p.id" :value="p.id" :label="p.name" /></el-select></el-form-item>
      <el-form-item label="随车护士" prop="nurseId"><el-select v-model="form.nurseId" aria-label="随车护士" placeholder="选择护士" clearable style="width:100%"><el-option v-for="p in resources.nurses" :key="p.id" :value="p.id" :label="p.name" /></el-select></el-form-item>
      <el-button type="primary" :loading="saving" @click="dispatch">确认派车</el-button>
    </el-form>
    <p v-if="!loading && !error && (!resources.vehicles.length || !resources.drivers.length || (order.serviceLevel === 'MONITORING' && !resources.nurses.length))" class="quote-unavailable">可用资源不足，请先在车辆管理、人员管理中补齐资料，或等待当前任务完成。</p>
  </section>
</template>
