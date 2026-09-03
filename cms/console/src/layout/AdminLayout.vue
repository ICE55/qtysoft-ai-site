<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-mark">QTY</span>
        <span class="logo-text">CMS 控制台</span>
      </div>
      <el-menu :default-active="activeMenu" class="menu" router background-color="transparent">
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon><span>仪表盘</span>
        </el-menu-item>
        <el-sub-menu index="pages">
          <template #title>
            <el-icon><Document /></el-icon><span>页面内容</span>
          </template>
          <el-menu-item v-for="p in pages" :key="p.key" :index="`/edit/${p.key}`">
            {{ p.label }}
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-if="isSuper" index="/system/users">
          <el-icon><Setting /></el-icon><span>账号管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <el-tag v-if="mustChange" type="warning" size="small">请修改初始密码</el-tag>
          <span class="user">{{ auth.user?.displayName || auth.user?.username }}</span>
          <el-dropdown @command="onCommand">
            <el-button text><el-icon><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitPwd">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const isSuper = computed(() => auth.role === 'SUPER_ADMIN')
const mustChange = computed(() => auth.mustChangePassword)

const pages = [
  { key: 'site', label: '站点设置' },
  { key: 'home', label: '首页' },
  { key: 'product', label: '产品能力' },
  { key: 'solutions', label: '行业方案' },
  { key: 'cases', label: '客户案例' },
  { key: 'about', label: '关于我们' }
]

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => {
  if (route.path === '/dashboard') return '仪表盘'
  if (route.path.startsWith('/edit/')) return pages.find(p => `/edit/${p.key}` === route.path)?.label || '编辑'
  if (route.path.startsWith('/history/')) return '发布历史'
  if (route.path.startsWith('/system')) return '账号管理'
  return 'CMS'
})

const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  } else if (cmd === 'pwd') {
    pwdVisible.value = true
  }
}

async function submitPwd() {
  pwdLoading.value = true
  try {
    await auth.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改')
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.layout { height: 100%; }
.aside {
  background: var(--bg-soft);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid var(--border);
}
.logo-mark {
  font-weight: 800;
  font-size: 18px;
  background: linear-gradient(90deg, var(--brand), var(--brand-2));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.logo-text { color: var(--text-2); font-size: 13px; }
.menu { border-right: none; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
}
.header-title { font-weight: 600; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user { color: var(--text-2); }
.main { background: var(--bg); padding: 24px; }
</style>
