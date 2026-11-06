<script setup lang="ts">
import OrderBoard from '../../components/OrderBoard.vue'
</script>
<template><section class="page-heading"><div><div class="eyebrow">ORDER MARKETPLACE</div><h1>订单池</h1><p>有效资质与对应等级的空闲车辆，满足条件即可参与报价。</p></div><el-tag effect="plain">每 10 秒自动刷新</el-tag></section><OrderBoard mode="COMPANY" /></template>
