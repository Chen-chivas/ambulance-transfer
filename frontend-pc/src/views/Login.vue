<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { request, messageOf } from '../api/http'
import { setSession, type User } from '../api/session'
import { homeFor } from '../router'

const route = useRoute()
const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')
async function login() {
  if (loading.value || !form.username.trim() || !form.password) return
  loading.value = true; error.value = ''
  try {
    const result = await request<{ token: string; user: User }>('/auth/login', 'POST', form)
    setSession(result.token, result.user)
    form.password = ''
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect.startsWith('/') && !redirect.startsWith('//') && !redirect.startsWith('/login') ? redirect : homeFor(result.user.role))
  } catch (e) { error.value = messageOf(e) }
  finally { loading.value = false }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story">
      <div class="brand"><span class="brand-mark">+</span><div>安心转运<small>救护车调度工作台</small></div></div>
      <div><div class="eyebrow">CARE IN EVERY TRANSFER</div><h1>让每一次转运<br>都有序、有保障。</h1><p>连接车行与调度，认真对待每一程。</p></div>
      <p>车行资料 · 资质审核 · 车辆管理</p>
    </section>
    <section class="login-panel">
      <form @submit.prevent="login">
        <div class="eyebrow">WELCOME BACK</div><h1>登录工作台</h1><p class="login-hint">请使用管理员分配的账号登录。</p>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" />
        <label for="username">账号</label><el-input id="username" v-model="form.username" autocomplete="username" placeholder="请输入账号" maxlength="50" size="large" :disabled="loading" />
        <label for="password">密码</label><el-input id="password" v-model="form.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" maxlength="72" size="large" :disabled="loading" />
        <el-button native-type="submit" type="primary" size="large" :loading="loading" :disabled="!form.username.trim() || !form.password" class="login-submit">登录</el-button>
        <p>登录后将进入所属角色的工作空间。</p>
      </form>
    </section>
  </div>
</template>
