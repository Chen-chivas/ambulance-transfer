<template>
  <el-result icon="warning" title="页面不存在" sub-title="请通过左侧导航进入工作台。"><template #extra><el-button type="primary" @click="$router.push('/')">返回首页</el-button></template></el-result>
</template>
