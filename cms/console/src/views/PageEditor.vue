<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <el-button text :icon="ArrowLeft" @click="$router.push('/dashboard')">返回</el-button>
        <h2 style="margin: 6px 0 2px">{{ title }}</h2>
        <p class="muted">编辑后先「保存草稿」，确认无误再「发布」到线上。</p>
      </div>
      <div class="actions">
        <el-button :loading="saving" @click="save">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :lg="14">
        <el-card v-for="sec in schema.sections" :key="sec.key" class="sec-card" shadow="never">
          <template #header><span class="sec-title">{{ sec.label }}</span></template>
          <SchemaField
            v-for="f in sec.fields"
            :key="f.key"
            :field="f"
            v-model="data[sec.key][f.key]"
          />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card class="preview-card" shadow="never">
          <template #header>
            <span class="sec-title">实时预览（内容结构）</span>
          </template>
          <PreviewPane :schema="schema" :data="data" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SchemaField from '@/components/SchemaField.vue'
import PreviewPane from '@/components/PreviewPane.vue'
import { getSchema, getDraft, saveDraft, publish as apiPublish } from '@/api/content'

const route = useRoute()
const docKey = computed(() => route.params.docKey)
const title = computed(() =>
  ({ site: '站点设置', home: '首页', product: '产品能力', solutions: '行业方案', cases: '客户案例', about: '关于我们' }[docKey.value] || docKey.value)
)

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const schema = ref({ sections: [] })
const data = ref({})

const LABELS = { site: '站点设置', home: '首页', product: '产品能力', solutions: '行业方案', cases: '客户案例', about: '关于我们' }

onMounted(load)
watch(docKey, load)

async function load() {
  loading.value = true
  try {
    schema.value = await getSchema(docKey.value)
    const draft = await getDraft(docKey.value)
    data.value = ensureShape(draft)
  } finally {
    loading.value = false
  }
}

function ensureShape(draft) {
  const shaped = {}
  for (const sec of schema.value.sections) {
    shaped[sec.key] = shaped[sec.key] || {}
    for (const f of sec.fields) {
      const def = f.type === 'list' ? [] : (f.type === 'number' ? 0 : '')
      if (draft?.[sec.key]?.[f.key] === undefined) {
        shaped[sec.key][f.key] = def
      } else {
        shaped[sec.key][f.key] = draft[sec.key][f.key]
      }
    }
  }
  return shaped
}

async function save() {
  saving.value = true
  try {
    await saveDraft(docKey.value, data.value)
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function publish() {
  try {
    const { value: note } = await ElMessageBox.prompt('发布备注（可选）', '确认发布', {
      confirmButtonText: '发布',
      cancelButtonText: '取消',
      inputPlaceholder: '如：更新首页案例'
    }).catch(() => ({ value: null }))
    if (note === null) return
    publishing.value = true
    await apiPublish(docKey.value, note || '')
    ElMessage.success('已发布，约 1–2 分钟生效')
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.page-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  margin-bottom: 16px; gap: 16px; flex-wrap: wrap;
}
.muted { color: var(--text-3); font-size: 13px; margin: 0; }
.actions { display: flex; gap: 10px; }
.sec-card { margin-bottom: 16px; }
.sec-title { font-weight: 600; }
.preview-card { position: sticky; top: 16px; }
</style>
