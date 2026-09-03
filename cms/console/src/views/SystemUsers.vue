<template>
  <div>
    <div class="page-head">
      <div>
        <h2>账号管理</h2>
        <p class="muted">仅超级管理员可管理 CMS 账号。初始密码登录后需强制修改。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增账号</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="displayName" label="显示名" />
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-tag :type="row.role === 'SUPER_ADMIN' ? 'danger' : row.role === 'EDITOR' ? 'primary' : 'info'" size="small">
            {{ roleLabel(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="强制改密" width="100">
        <template #default="{ row }">{{ row.mustChangePassword ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑账号' : '新增账号'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名" v-if="!editing">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role">
            <el-option label="超级管理员" value="SUPER_ADMIN" />
            <el-option label="内容编辑" value="EDITOR" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="editing ? '重置密码' : '初始密码'">
          <el-input v-model="form.password" type="password" show-password :placeholder="editing ? '留空则不修改' : ''" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, deleteUser } from '@/api/system'

const list = ref([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref(null)
const submitting = ref(false)
const form = ref({ username: '', displayName: '', role: 'EDITOR', password: '' })

onMounted(load)
async function load() {
  loading.value = true
  try {
    list.value = await listUsers()
  } finally {
    loading.value = false
  }
}
function roleLabel(r) {
  return { SUPER_ADMIN: '超级管理员', EDITOR: '内容编辑', VIEWER: '只读' }[r] || r
}
function formatTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : '—'
}
function openCreate() {
  editing.value = null
  form.value = { username: '', displayName: '', role: 'EDITOR', password: '' }
  dialog.value = true
}
function openEdit(row) {
  editing.value = row.id
  form.value = { username: row.username, displayName: row.displayName || '', role: row.role, password: '' }
  dialog.value = true
}
async function submit() {
  submitting.value = true
  try {
    if (editing.value) {
      await updateUser(editing.value, {
        role: form.value.role,
        displayName: form.value.displayName,
        password: form.value.password || undefined
      })
    } else {
      await createUser({
        username: form.value.username,
        displayName: form.value.displayName,
        role: form.value.role,
        password: form.value.password
      })
    }
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } finally {
    submitting.value = false
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除账号 ${row.username}？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<style scoped>
.page-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; gap: 16px; flex-wrap: wrap; }
.muted { color: var(--text-3); font-size: 13px; margin: 0; }
</style>
