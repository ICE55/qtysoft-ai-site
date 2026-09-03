<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="never">
      <div class="brand">
        <span class="mark">QTY</span>
        <span class="name">乾腾元 CMS</span>
      </div>
      <h2 class="title">控制台登录</h2>
      <p class="sub">登录后即可维护官网内容，无需改动源码</p>
      <el-form :model="form" @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    show-password :prefix-icon="Lock" @keyup.enter="onSubmit" />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>
      <p class="hint">首次登录请使用种子管理员账号（见部署文档），并尽快修改初始密码。</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    const redirect = route.query.redirect || '/dashboard'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(800px 400px at 70% -10%, rgba(59,123,255,0.18), transparent),
    radial-gradient(700px 400px at 10% 110%, rgba(139,92,246,0.16), transparent),
    var(--bg);
}
.login-card {
  width: 380px;
  padding: 8px 28px 24px;
}
.brand { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.mark {
  font-weight: 800; font-size: 22px;
  background: linear-gradient(90deg, var(--brand), var(--brand-2));
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.name { color: var(--text-2); }
.title { margin: 18px 0 4px; }
.sub { color: var(--text-3); margin: 0 0 18px; font-size: 13px; }
.submit { width: 100%; }
.hint { color: var(--text-3); font-size: 12px; margin-top: 14px; }
</style>
