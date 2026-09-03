<template>
  <div class="preview">
    <div v-for="sec in schema.sections" :key="sec.key" class="preview-section">
      <div class="sec-title">{{ sec.label }}</div>
      <div v-for="f in sec.fields" :key="f.key" class="field-line">
        <template v-if="f.type === 'list'">
          <div class="field-name">{{ f.label }}（{{ arrLen(data?.[sec.key]?.[f.key]) }}）</div>
          <div class="list-preview">
            <div v-for="(item, i) in data?.[sec.key]?.[f.key] || []" :key="i" class="list-card">
              <div v-for="sub in f.itemFields" :key="sub.key" class="kv">
                <span class="k">{{ sub.label }}</span>
                <span class="v">{{ item?.[sub.key] }}</span>
              </div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="kv">
            <span class="k">{{ f.label }}</span>
            <span class="v">{{ data?.[sec.key]?.[f.key] }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  schema: { type: Object, required: true },
  data: { type: Object, default: () => ({}) }
})
function arrLen(v) {
  return Array.isArray(v) ? v.length : 0
}
</script>

<style scoped>
.preview { font-size: 13px; color: var(--text); }
.preview-section {
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: var(--bg-soft);
}
.sec-title { font-weight: 700; margin-bottom: 8px; color: var(--brand); }
.field-line { margin: 6px 0; }
.field-name { color: var(--text-2); font-size: 12px; margin-bottom: 4px; }
.kv { display: flex; gap: 8px; padding: 2px 0; }
.k { color: var(--text-3); min-width: 96px; }
.v { color: var(--text); white-space: pre-wrap; }
.list-preview { display: grid; gap: 8px; }
.list-card {
  border: 1px dashed var(--border-strong);
  border-radius: 8px;
  padding: 8px 10px;
  background: var(--bg-card);
}
</style>
