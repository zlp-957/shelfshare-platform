<template>
  <el-card shadow="never">
    <template #header>
      <div class="header-row">
        <div class="header-left">
          <el-input
            v-model="keyword"
            placeholder="账号 / 动作 / 内容"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="module"
            placeholder="全部模块"
            clearable
            style="width: 150px; margin-left: 8px"
            @change="handleSearch"
          >
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">搜索</el-button>
        </div>
        <span class="tip">记录所有会改变资金或他人状态的操作，含失败尝试</span>
      </div>
    </template>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail-box">
            <div class="detail-line">
              <span class="detail-label">入参</span>
              <code class="detail-code">{{ pretty(row.detail) }}</code>
            </div>
            <div v-if="row.errorMsg" class="detail-line">
              <span class="detail-label">失败原因</span>
              <code class="detail-code error">{{ row.errorMsg }}</code>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column prop="userAccount" label="操作人" width="130">
        <template #default="{ row }">{{ row.userAccount || '—' }}</template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column prop="action" label="动作" min-width="150" />
      <el-table-column prop="ip" label="来源 IP" width="150">
        <template #default="{ row }">
          <span class="mono">{{ row.ip || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.success === 1" type="success" effect="light">成功</el-tag>
          <el-tag v-else type="danger" effect="light">失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="摘要" min-width="220">
        <template #default="{ row }">
          <span class="summary">{{ truncate(row.detail) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="handlePageChange"
      />
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { pageOperationLogs } from '../../api'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const module = ref(null)
const keyword = ref('')

// 与后端 @AuditLog 上打的 module 保持一致
const modules = ['借阅', '读者', '馆藏', '书目', '资金', '支付']

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageOperationLogs({
      page: page.value,
      size: size.value,
      module: module.value || undefined,
      keyword: keyword.value || undefined
    })
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadData()
}

const handlePageChange = (p) => {
  page.value = p
  loadData()
}

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '—')

const truncate = (s) => {
  if (!s) return '—'
  return s.length > 80 ? s.slice(0, 80) + '…' : s
}

const pretty = (s) => {
  if (!s) return '（无入参）'
  try {
    return JSON.stringify(JSON.parse(s), null, 2)
  } catch (e) {
    return s
  }
}

onMounted(loadData)
</script>

<style scoped>
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
}
.tip {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.detail-box {
  padding: 12px 20px 16px 56px;
  background: var(--paper-sunken);
}
.detail-line {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}
.detail-line:last-child {
  margin-bottom: 0;
}
.detail-label {
  flex-shrink: 0;
  width: 64px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
  padding-top: 3px;
}
.detail-code {
  flex: 1;
  margin: 0;
  padding: 8px 10px;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--text-secondary);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  white-space: pre-wrap;
  word-break: break-all;
}
.detail-code.error {
  color: var(--el-color-danger);
}
.mono {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-secondary);
}
.summary {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--sp-4);
}
</style>
