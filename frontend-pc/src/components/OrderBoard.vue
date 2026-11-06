<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { listOrders, accepting, money, orderLabels, orderTime, quoteLabels, type OrderMode, type TransferOrder } from '../api/orders'
import { messageOf } from '../api/http'
import { usePolling } from '../composables/usePolling'
import OrderDetails from './OrderDetails.vue'

const props = defineProps<{ mode: OrderMode }>()
const rows = ref<TransferOrder[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref(props.mode === 'DISPATCH' ? 'CONFIRMED' : '')
const level = ref('')
const tab = ref('pool')
const loading = ref(false)
const error = ref('')
const refreshedAt = ref('')
const selectedId = ref<number | null>(null)
const drawer = ref(false)
let sequence = 0
async function load() {
  const seq = ++sequence
  loading.value = true; error.value = ''
  try {
    const result = await listOrders(props.mode, tab.value, { keyword: keyword.value, status: status.value, serviceLevel: level.value, page: page.value, size: size.value })
    if (seq !== sequence) return
    rows.value = result.items; total.value = result.total; refreshedAt.value = new Date().toLocaleTimeString('zh-CN')
    if (page.value > 1 && !result.items.length) { page.value--; await load() }
  } catch (e) { if (seq === sequence) { error.value = messageOf(e); rows.value = []; total.value = 0 } }
  finally { if (seq === sequence) loading.value = false }
}
function search() { page.value = 1; void load() }
function open(row: TransferOrder) { selectedId.value = row.id; drawer.value = true }
async function created(row: TransferOrder) { page.value = 1; keyword.value = ''; status.value = ''; level.value = ''; await load(); open(row) }
defineExpose({ created, load })
onMounted(load)
usePolling(() => { if (!loading.value && !drawer.value) void load() })
onUnmounted(() => { sequence++ })
</script>

<template>
  <el-card shadow="never" class="business-card">
    <el-tabs v-if="mode === 'COMPANY'" v-model="tab" @tab-change="search"><el-tab-pane label="可报价订单" name="pool" /><el-tab-pane label="我的报价与中标" name="mine" /></el-tabs>
    <div class="toolbar"><form class="filter-form order-filter" @submit.prevent="search"><el-input v-model="keyword" placeholder="搜索订单号 / 起点 / 终点" aria-label="搜索订单" clearable maxlength="100" /><el-select v-model="level" placeholder="全部服务等级" aria-label="服务等级筛选" clearable @change="search"><el-option label="基础型" value="BASIC" /><el-option label="监护型" value="MONITORING" /></el-select><el-select v-model="status" placeholder="全部订单状态" aria-label="订单状态筛选" clearable @change="search"><el-option v-for="(label, value) in orderLabels" :key="value" :label="label" :value="value" /></el-select><el-button native-type="submit" :loading="loading">查询</el-button><el-button @click="keyword = ''; status = ''; level = ''; search()">重置</el-button></form></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无符合条件的订单">
      <el-table-column label="订单 / 创建时间" min-width="215"><template #default="{ row }"><el-button link type="primary" class="order-number" @click="open(row)">{{ row.orderNo }}</el-button><p>{{ orderTime(row.createdAt) }}</p></template></el-table-column>
      <el-table-column label="转运路线" min-width="220"><template #default="{ row }"><div class="route-line"><span class="route-dot origin"></span>{{ row.originAddress }}</div><div class="route-line"><span class="route-dot destination"></span>{{ row.destAddress }}</div></template></el-table-column>
      <el-table-column label="服务等级" width="100"><template #default="{ row }">{{ row.serviceLevel === 'BASIC' ? '基础型' : '监护型' }}</template></el-table-column>
      <el-table-column label="订单状态" width="120"><template #default="{ row }"><el-tag :type="accepting(row.status) ? 'warning' : row.status === 'CONFIRMED' ? 'success' : 'info'">{{ orderLabels[row.status as keyof typeof orderLabels] }}</el-tag></template></el-table-column>
      <el-table-column v-if="mode === 'COMPANY'" label="我的报价" min-width="125"><template #default="{ row }">{{ money(row.myAmount) }}<p v-if="row.myQuoteStatus">{{ quoteLabels[row.myQuoteStatus as keyof typeof quoteLabels] }}</p></template></el-table-column>
      <el-table-column v-else label="确认车行 / 金额" min-width="140"><template #default="{ row }">{{ row.assignedCompanyName || '待选标' }}<p>{{ money(row.finalAmount) }}</p></template></el-table-column>
      <el-table-column label="操作" width="105" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="open(row)">{{ mode === 'COMPANY' && accepting(row.status) ? '查看 / 报价' : mode === 'MOCK' && accepting(row.status) ? '查看报价' : '查看详情' }}</el-button></template></el-table-column>
    </el-table>
    <div class="table-footer"><p>每 10 秒刷新<span v-if="refreshedAt"> · 最近更新 {{ refreshedAt }}</span></p><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50]" layout="total, prev, pager, next, sizes" @current-change="load" @size-change="search" /></div>
  </el-card>
  <el-drawer v-model="drawer" title="转运订单详情" size="680px" destroy-on-close @closed="load"><OrderDetails v-if="selectedId" :key="selectedId" :id="selectedId" :mode="mode" @changed="load" /></el-drawer>
</template>
