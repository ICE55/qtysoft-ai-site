<template>
  <div>
    <div class="page-head">
      <h2>内容概览</h2>
      <p class="muted">点击任意内容区进入编辑；橙色标记为「有未发布改动」。</p>
    </div>

    <el-row :gutter="16">
      <el-col v-for="item in list" :key="item.key" :xs="24" :sm="12" :lg="8">
        <el-card class="doc-card" shadow="hover" @click="goEdit(item.key)">
          <div class="card-top">
            <span class="doc-label">{{ item.label }}</span>
            <el-tag v-if="item.hasUnpublishedChanges" type="warning" size="small">有未发布改动</el-tag>
            <el-tag v-else-if="item.status === 'PUBLISHED'" type="success" size="small">已发布</el-tag>
            <el-tag v-else type="info" size="small">未发布</el-tag>
          </div>
          <div class="card-meta">
            <span>更新：{{ formatTime(item.updatedAt) }}</span>
          </div>
          <el-button text type="primary" class="card-action">编辑 →</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="deploy-card" shadow="never">
      <div class="deploy-row">
        <div>
          <div class="deploy-title">手动触发静态站重建</div>
          <div class="muted">当你已在控制台完成内容发布，可手动再次触发 Pages / Vercel 重建。</div>
        </div>
        <el-button :loading="deploying" @click="triggerDeploy">触发重建</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSummary } from '@/api/content'
import { triggerDeploy as apiTrigger } from '@/api/system'
import { ElMessage } from 'element-plus'

const router = useRouter()
const list = ref([])
const deploying = ref(false)

onMounted(load)
async function load() {
  list.value = await getSummary()
}
function goEdit(key) {
  router.push(`/edit/${key}`)
}
function formatTime(t) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN')
}
async function triggerDeploy() {
  deploying.value = true
  try {
    const res = await apiTrigger()
    if (res.triggered) ElMessage.success('已触发重建，约 1–2 分钟生效')
    else ElMessage.warning('未配置部署钩子，请在后端设置 DEPLOY_HOOK_URL')
  } finally {
    deploying.value = false
  }
}
</script>

<style scoped>
.page-head { margin-bottom: 18px; }
.page-head h2 { margin: 0 0 4px; }
.muted { color: var(--text-3); font-size: 13px; margin: 0; }
.doc-card { cursor: pointer; margin-bottom: 16px; }
.card-top { display: flex; align-items: center; justify-content: space-between; }
.doc-label { font-weight: 600; font-size: 15px; }
.card-meta { color: var(--text-3); font-size: 12px; margin: 10px 0; }
.card-action { padding-left: 0; }
.deploy-card { margin-top: 8px; }
.deploy-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.deploy-title { font-weight: 600; margin-bottom: 4px; }
</style>
