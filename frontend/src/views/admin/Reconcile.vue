<template>
  <div class="reconcile-page">
    <el-card shadow="never" class="run-card">
      <template #header>
        <div class="header-row">
          <div class="header-left">
            <span class="card-title">资金对账</span>
            <span class="card-desc">拉取渠道账单与本地支付单逐笔核对，只列出对不上的记录</span>
          </div>
        </div>
      </template>

      <div class="run-bar">
        <el-date-picker
          v-model="billDate"
          type="date"
          placeholder="对账日期"
          value-format="YYYY-MM-DD"
          :disabled-date="disableFuture"
          style="width: 180px"
        />
        <el-button type="primary" :loading="running" @click="handleRun">
          {{ running ? '对账中…' : '开始对账' }}
        </el-button>
        <span class="hint">定时任务每天 02:00 自动对前一天的账</span>
      </div>

      <div v-if="result" class="result">
        <div class="stat">
          <span class="stat-num">{{ result.localCount }}</span>
          <span class="stat-label">本地已支付单</span>
        </div>
        <div class="stat">
          <span class="stat-num">{{ result.channelCount }}</span>
          <span class="stat-label">渠道账单笔数</span>
        </div>
        <div class="stat" :class="{ danger: result.diffCount > 0 }">
          <span class="stat-num">{{ result.diffCount }}</span>
          <span class="stat-label">差异笔数</span>
        </div>
        <el-alert
          v-if="result.simulated || (result.channelCount === 0 && result.localCount > 0)"
          type="warning"
          :closable="false"
          show-icon
          class="sim-alert"
          :title="result.message"
        />
        <el-alert
          v-else-if="result.diffCount === 0"
          type="success"
          :closable="false"
          show-icon
          class="sim-alert"
          title="账实一致，未发现差异"
        />
      </div>
    </el-card>

    <el-card shadow="never" class="diff-card">
      <template #header>
        <div class="header-row">
          <div class="header-left">
            <span class="card-title">差异记录</span>
          </div>
          <div class="header-right">
            <el-select
              v-model="status"
              placeholder="全部状态"
              clearable
              style="width: 140px"
              @change="handleSearch"
            >
              <el-option label="待处理" :value="0" />
              <el-option label="已处理" :value="1" />
              <el-option label="已忽略" :value="2" />
            </el-select>
            <el-input
              v-model="keyword"
              placeholder="批次号 / 支付单号"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="batchNo" label="批次" width="130" />
        <el-table-column label="账期" width="120">
          <template #default="{ row }">{{ row.billDate }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" label="支付单号" min-width="210" show-overflow-tooltip />
        <el-table-column label="借阅单" width="90">
          <template #default="{ row }">{{ row.borrowId || '—' }}</template>
        </el-table-column>
        <el-table-column label="差异类型" width="150">
          <template #default="{ row }">
            <el-tag :type="diffTagType(row.diffType)" effect="light">
              {{ diffText(row.diffType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="本地金额" width="110" align="right">
          <template #default="{ row }">{{ row.localAmount == null ? '—' : '¥' + row.localAmount }}</template>
        </el-table-column>
        <el-table-column label="渠道金额" width="110" align="right">
          <template #default="{ row }">{{ row.channelAmount == null ? '—' : '¥' + row.channelAmount }}</template>
        </el-table-column>
        <el-table-column label="差额" width="100" align="right">
          <template #default="{ row }">
            <span :class="row.diffAmount > 0 ? 'amt-warn' : ''">
              {{ row.diffAmount == null ? '—' : '¥' + row.diffAmount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="warning">待处理</el-tag>
            <el-tag v-else-if="row.status === 1" type="success">已处理</el-tag>
            <el-tag v-else type="info">已忽略</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="handleResolve(row)">已处理</el-button>
              <el-button link type="info" @click="handleIgnore(row)">忽略</el-button>
            </template>
            <span v-else class="done-text">—</span>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { runReconcile, pageReconcile, handleReconcile } from '../../api'

const billDate = ref(yesterday())
const running = ref(false)
const result = ref(null)

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const status = ref(null)
const keyword = ref('')

function yesterday() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const disableFuture = (date) => date.getTime() > Date.now()

const handleRun = async () => {
  if (!billDate.value) {
    ElMessage.warning('请选择对账日期')
    return
  }
  running.value = true
  try {
    const res = await runReconcile({ date: billDate.value })
    result.value = res.data
    ElMessage.success('对账完成')
    loadData()
  } finally {
    running.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageReconcile({
      page: page.value,
      size: size.value,
      status: status.value === null ? undefined : status.value,
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

const handleResolve = async (row) => {
  const { value } = await ElMessageBox.prompt('请填写处理说明', '标记为已处理', {
    inputPlaceholder: '如：已补记流水 / 已核实为误报',
    inputValidator: (v) => (v && v.trim() ? true : '请填写处理说明')
  })
  await handleReconcile(row.id, { resolved: true, remark: value })
  ElMessage.success('已处理')
  loadData()
}

const handleIgnore = async (row) => {
  const { value } = await ElMessageBox.prompt('请填写忽略原因', '忽略该差异', {
    inputPlaceholder: '如：测试单，无需处理',
    inputValidator: (v) => (v && v.trim() ? true : '请填写忽略原因')
  })
  await handleReconcile(row.id, { resolved: false, remark: value })
  ElMessage.success('已忽略')
  loadData()
}

const diffText = (type) => {
  if (type === 1) return '本地有·渠道无'
  if (type === 2) return '渠道有·本地无'
  if (type === 4) return '退款滞留'
  return '金额不符'
}

const diffTagType = (type) => {
  if (type === 1) return 'danger'
  if (type === 2) return 'warning'
  // 退款滞留说明用户的钱一直没退出去，和「疑似伪造回调」同级别
  if (type === 4) return 'danger'
  return 'info'
}

onMounted(loadData)
</script>

<style scoped>
.reconcile-page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-title {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}
.card-desc {
  font-size: var(--fs-sm);
  color: var(--text-muted);
}
.run-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.result {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  margin-top: var(--sp-5);
  padding-top: var(--sp-5);
  border-top: 1px dashed var(--line);
  flex-wrap: wrap;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 96px;
}
.stat-num {
  font-family: var(--font-serif);
  font-size: 26px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.1;
}
.stat.danger .stat-num {
  color: var(--el-color-danger);
}
.stat-label {
  font-size: var(--fs-xs);
  color: var(--text-muted);
  letter-spacing: 0.04em;
}
.sim-alert {
  flex: 1;
  min-width: 280px;
}
.amt-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}
.done-text {
  color: var(--text-muted);
}
.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--sp-4);
}
</style>
