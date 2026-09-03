<template>
  <!-- 列表 -->
  <div v-if="field.type === 'list'" class="list-field">
    <div class="list-head">
      <span class="field-label">{{ field.label }}</span>
      <el-button size="small" :icon="Plus" @click="addItem">新增{{ field.itemLabel || '项' }}</el-button>
    </div>
    <div v-for="(item, i) in list" :key="i" class="list-item">
      <div class="list-item-body">
        <SchemaField
          v-for="sub in field.itemFields"
          :key="sub.key"
          :field="sub"
          v-model="item[sub.key]"
        />
      </div>
      <div class="list-item-ops">
        <el-button text size="small" :icon="Top" :disabled="i === 0" @click="move(i, -1)" />
        <el-button text size="small" :icon="Bottom" :disabled="i === list.length - 1" @click="move(i, 1)" />
        <el-button text size="small" type="danger" :icon="Delete" @click="remove(i)" />
      </div>
    </div>
    <el-empty v-if="!list.length" :description="`暂无${field.itemLabel || '项'}`" :image-size="48" />
  </div>

  <!-- 单行文本 / 邮箱 -->
  <el-form-item v-else-if="isTextLike" :label="field.label" :required="field.required">
    <el-input
      v-model="model"
      :type="field.type === 'textarea' ? 'textarea' : 'text'"
      :rows="field.type === 'textarea' ? 3 : undefined"
      :maxlength="field.maxLength || undefined"
      :show-word-limit="!!field.maxLength"
      :placeholder="field.label"
    />
  </el-form-item>

  <!-- 数字 -->
  <el-form-item v-else-if="field.type === 'number'" :label="field.label" :required="field.required">
    <el-input-number v-model="numModel" :controls="false" />
  </el-form-item>

  <!-- 布尔 -->
  <el-form-item v-else-if="field.type === 'boolean'" :label="field.label">
    <el-switch v-model="boolModel" />
  </el-form-item>

  <!-- 下拉 -->
  <el-form-item v-else-if="field.type === 'select'" :label="field.label" :required="field.required">
    <el-select v-model="model" placeholder="请选择">
      <el-option v-for="opt in field.options || []" :key="opt" :label="opt" :value="opt" />
    </el-select>
  </el-form-item>
</template>

<script setup>
import { computed } from 'vue'
import { Plus, Delete, Top, Bottom } from '@element-plus/icons-vue'

const props = defineProps({
  field: { type: Object, required: true }
})
const model = defineModel()

const isTextLike = computed(() =>
  ['text', 'textarea', 'email'].includes(props.field.type)
)

const list = computed({
  get: () => (Array.isArray(model.value) ? model.value : []),
  set: (v) => { model.value = v }
})

function addItem() {
  const item = {}
  ;(props.field.itemFields || []).forEach(f => { item[f.key] = f.type === 'number' ? 0 : '' })
  if (!Array.isArray(model.value)) model.value = []
  model.value.push(item)
}
function remove(i) {
  model.value.splice(i, 1)
}
function move(i, dir) {
  const arr = model.value
  const j = i + dir
  if (j < 0 || j >= arr.length) return
  const tmp = arr[i]
  arr[i] = arr[j]
  arr[j] = tmp
}

const numModel = computed({
  get: () => (typeof model.value === 'number' ? model.value : Number(model.value) || 0),
  set: (v) => { model.value = v }
})
const boolModel = computed({
  get: () => !!model.value,
  set: (v) => { model.value = v }
})
</script>

<style scoped>
.list-field { margin-bottom: 16px; }
.list-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.field-label { font-weight: 600; color: var(--text-2); font-size: 13px; }
.list-item {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  background: var(--bg-soft);
}
.list-item-body { display: grid; gap: 4px; }
.list-item-ops { display: flex; justify-content: flex-end; gap: 4px; margin-top: 6px; }
</style>
