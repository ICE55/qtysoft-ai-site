<template>
  <div>
    <div class="page-head">
      <div>
        <el-button text :icon="ArrowLeft" @click="$router.push('/dashboard')">返回</el-button>
        <h2 style="margin: 6px 0 2px">发布历史 · {{ title }}</h2>
        <p class="muted">每次发布都会生成版本快照，可一键回滚到任意历史版本。</p>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" style="width: 100%" border>
      <el-table-column prop="id" label="版本" width="90" />
      <el-table-column prop="note" label="备注" />
      <el-table-column prop="createdByName" label="操作人" width="140" />
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" :loading="restoring === row.id" @click="restore(row.id)">回滚</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !list.length" description="暂无发布记录" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getHistory, restore } from '@/api/content'

const route = useRoute()
const docKey = computed(() => route.params.docKey)
const title = computed(() =>
  ({ site: '站点设置', home: '首页', product: '产品能力', solutions: '行业方案', cases: '客户案例', about: '关于我们' }[docKey.value] || docKey.value)
)

const list = ref([])
const loading = ref(false)
const restoring = ref(null)

onMounted(load)
async function load() {
  loading.value = true
  try {
    list.value = await getHistory(docKey.value)
  } finally {
    loading.value = false
  }
}
function formatTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN') : '—'
}
async function restore(revId) {
  try {
    await ElMessageBox.confirm('确定回滚到该版本？将以该版本内容重新发布。', '回滚确认', {
      confirmButtonText: '回滚', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  restoring.value = revId
  try {
    await restore(docKey.value, revId)
    ElMessage.success('已回滚并重新发布')
    await load()
  } finally {
    restoring.value = null
  }
}
</script>

<style scoped>
.page-head { margin-bottom: 16px; }
.muted { color: var(--text-3); font-size: 13px; margin: 0; }
</style>
