<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="720px"
    top="5vh"
    :close-on-click-modal="false"
    class="agreement-dialog"
  >
    <div class="agreement-body">
      <div class="agreement-meta">
        <el-tag size="small" effect="plain">版本 {{ version }}</el-tag>
        <span class="meta-hint">请仔细阅读以下条款，勾选后才能继续操作</span>
      </div>
      <div class="agreement-content" v-html="rendered"></div>
    </div>

    <template #footer>
      <div class="agreement-footer">
        <el-checkbox v-model="checked">我已阅读并知晓上述规则</el-checkbox>
        <div class="footer-actions">
          <el-button @click="visible = false">暂不操作</el-button>
          <el-button type="primary" :disabled="!checked" :loading="submitting" @click="confirm">
            确认并继续
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAgreement, agreeAgreement } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 1 借阅规则 2 购买须知
  type: { type: Number, default: 1 }
})
const emit = defineEmits(['update:modelValue', 'agreed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const submitting = ref(false)
const checked = ref(false)
const version = ref('')
const content = ref('')
const loadedType = ref(null)

const dialogTitle = computed(() =>
  props.type === 2 ? '购买须知' : '借阅规则'
)

// 极简 markdown 渲染：标题、段落、列表、表格，避免额外引入依赖
const rendered = computed(() => {
  if (!content.value) return '<p class="empty">加载中…</p>'
  const escape = (s) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const lines = content.value.split('\n')
  const out = []
  let i = 0
  while (i < lines.length) {
    const t = lines[i].trim()
    // 表格：连续的 | 开头的行合成一个 <table>
    if (t.startsWith('|')) {
      const tableLines = []
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        tableLines.push(lines[i].trim())
        i++
      }
      out.push(renderTable(tableLines, escape))
      continue
    }
    if (!t) {
      i++
      continue
    }
    if (t.startsWith('### ')) out.push(`<h4>${escape(t.slice(4))}</h4>`)
    else if (t.startsWith('## ')) out.push(`<h3>${escape(t.slice(3))}</h3>`)
    else if (t.startsWith('# ')) out.push(`<h2>${escape(t.slice(2))}</h2>`)
    else if (t.startsWith('> ')) out.push(`<p class="quote">${escape(t.slice(2))}</p>`)
    else if (/^\d+\.\s/.test(t) || t.startsWith('- ')) out.push(`<p class="li">${escape(t)}</p>`)
    else out.push(`<p>${escape(t)}</p>`)
    i++
  }
  return out.join('')
})

function renderTable(lines, escape) {
  const cells = (line) =>
    line
      .split('|')
      .slice(1, -1)
      .map((c) => c.trim())
  const header = cells(lines[0])
  // 第二行是分隔线 |---|---|，跳过
  const body = lines.slice(2).map(cells)
  let html = '<table class="md-table"><thead><tr>'
  header.forEach((h) => {
    html += `<th>${escape(h)}</th>`
  })
  html += '</tr></thead><tbody>'
  body.forEach((row) => {
    html += '<tr>'
    row.forEach((c) => {
      html += `<td>${escape(c)}</td>`
    })
    html += '</tr>'
  })
  html += '</tbody></table>'
  return html
}

async function load() {
  if (loadedType.value === props.type) return
  loading.value = true
  try {
    const res = await getAgreement(props.type)
    version.value = res.data.version
    content.value = res.data.content
    loadedType.value = props.type
    checked.value = !!res.data.agreed
  } catch (e) {
    content.value = '规则加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      checked.value = false
      load()
    }
  },
  { immediate: true }
)

async function confirm() {
  submitting.value = true
  try {
    await agreeAgreement(props.type)
    emit('agreed')
    visible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '操作失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.agreement-body {
  max-height: 62vh;
  display: flex;
  flex-direction: column;
}

.agreement-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-subtle, rgba(0, 0, 0, 0.06));
  margin-bottom: 12px;
}

.meta-hint {
  font-size: 12px;
  color: var(--text-tertiary, #909399);
}

.agreement-content {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-secondary, #606266);
}

.agreement-content :deep(h2) {
  font-size: 16px;
  font-weight: 600;
  margin: 4px 0 12px;
  color: var(--text-primary, #303133);
}

.agreement-content :deep(h3) {
  font-size: 14px;
  font-weight: 600;
  margin: 18px 0 8px;
  color: var(--text-primary, #303133);
}

.agreement-content :deep(h4) {
  font-size: 13px;
  font-weight: 600;
  margin: 12px 0 6px;
}

.agreement-content :deep(p) {
  margin: 0 0 8px;
}

.agreement-content :deep(.li) {
  padding-left: 4px;
}

.agreement-content :deep(.quote) {
  color: var(--text-tertiary, #909399);
  font-size: 12px;
}

.agreement-content :deep(.table-line) {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  margin: 0;
  white-space: pre-wrap;
}

.agreement-content :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0 16px;
  font-size: 13px;
}

.agreement-content :deep(.md-table th),
.agreement-content :deep(.md-table td) {
  padding: 8px 12px;
  border: 1px solid var(--border-subtle, rgba(0, 0, 0, 0.08));
  text-align: left;
}

.agreement-content :deep(.md-table th) {
  background: var(--paper-sunken, #f5f5f3);
  font-weight: 600;
  color: var(--text-primary, #303133);
}

.agreement-content :deep(.md-table td) {
  color: var(--text-secondary, #606266);
}

.agreement-content :deep(.empty) {
  color: var(--text-tertiary, #909399);
}

.agreement-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.footer-actions {
  display: flex;
  gap: 8px;
}
</style>
