<script setup lang="ts">
import { currentUser } from '../api/session'
import { homeFor } from '../router'
</script>
<template><el-result icon="warning" title="无访问权限" sub-title="当前账号无法访问此页面。"><template #extra><RouterLink :to="homeFor(currentUser?.role)"><el-button type="primary">返回工作空间</el-button></RouterLink></template></el-result></template>
