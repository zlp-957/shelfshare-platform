<template>
  <header class="page-title">
    <h2>密码重置</h2>
    <span class="hint">读者忘记密码时的电话核实与发码入口</span>
  </header>

  <el-card shadow="never">
    <template #header>
      <div class="header-row">
        <span>密码重置申请</span>
        <el-radio-group v-model="statusFilter" @change="loadData">
          <el-radio-button :label="undefined">全部</el-radio-button>
          <el-radio-button :label="0">待核实</el-radio-button>
          <el-radio-button :label="1">已发码</el-radio-button>
          <el-radio-button :label="2">已完成</el-radio-button>
          <el-radio-button :label="3">已拒绝/过期</el-radio-button>
        </el-radio-group>
      </div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="page-tip"
      title="流程：读者提交申请 → 你打电话核实 → 点「核实并发码」生成 6 位重置码 → 口头告知读者 → 读者自行设置新密码。你全程看不到新密码。"
    />

    <!-- 加载中或已有数据时才渲染表格，避免表格自带空文案与下方 el-empty 重复 -->
    <el-table v-if="loading || list.length > 0" :data="list" v-loading="loading" stripe>
      <el-table-column prop="applyName" label="申请人" width="120" />
      <el-table-column label="手机号" width="150">
        <template #default="{ row }">
          <span class="phone">{{ row.applyPhone }}</span>
          <el-button text size="small" class="copy-btn" @click="copy(row.applyPhone)">复制</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="account" label="账号" width="140">
        <template #default="{ row }">
          <span class="mono">{{ row.account }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="light">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="200">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button type="primary" size="small" @click="handleGenerate(row)">核实并发码</el-button>
            <el-button type="danger" size="small" plain @click="handleReject(row)">拒绝</el-button>
          </template>
          <span v-else-if="row.status === 1" class="sent-cell">
            <span class="muted">已发码，{{ formatTime(row.codeExpire) }} 过期</span>
            <span class="sent-actions">
              <el-button type="warning" size="small" plain @click="handleGenerate(row)">重新发码</el-button>
              <el-button type="danger" size="small" plain @click="handleReject(row)">作废</el-button>
            </span>
          </span>
          <span v-else class="muted">{{ row.remark || '—' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && list.length === 0" description="暂无申请" />

    <div class="pagination" v-if="total > 0">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 重置码展示：只显示这一次，关闭后不再回显 -->
    <el-dialog v-model="codeVisible" title="重置码已生成" width="420px" :close-on-click-modal="false">
      <p class="code-hint">请把这 6 位码口头告知 {{ codeOwner }}，不要截图外发：</p>
      <div class="code-box">{{ codeValue }}</div>
      <p class="code-expire">30 分钟内有效，仅可使用一次。读者在登录页「忘记密码 → 使用重置码」里填写。</p>
      <template #footer>
        <el-button type="primary" @click="codeVisible = false">已告知，关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePasswordResets, generateResetCode, rejectPasswordReset } from '../../api'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const statusFilter = ref(undefined)

const codeVisible = ref(false)
const codeValue = ref('')
const codeOwner = ref('')

async function loadData() {
  loading.value = true
  try {
    const res = await pagePasswordResets({
      page: page.value,
      size: size.value,
      status: statusFilter.value
    })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(p) {
  page.value = p
  loadData()
}

async function handleGenerate(row) {
  const resend = row.status === 1
  try {
    await ElMessageBox.confirm(
      `确认已通过电话核实 ${row.applyName}（${row.applyPhone}）的身份？` +
        (resend
          ? '\n重新发码会立刻作废上一个码。'
          : '\n核实后将生成一次性重置码，对方可凭码修改密码。'),
      resend ? '重新发码' : '发码前确认',
      { type: 'warning', confirmButtonText: '已核实，发码' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await generateResetCode(row.id)
    codeValue.value = res.data.code
    codeOwner.value = `${row.applyName}（${row.account}）`
    codeVisible.value = true
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '生成失败')
  }
}

async function handleReject(row) {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt('拒绝原因（可选）：', '拒绝申请', {
      inputPlaceholder: '例如：电话无人接听、身份核验不通过',
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消'
    })
    remark = r.value || ''
  } catch (e) {
    return
  }
  try {
    await rejectPasswordReset(row.id, { remark })
    ElMessage.success('已拒绝')
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制手机号')
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择')
  }
}

function statusTagType(s) {
  switch (s) {
    case 0:
      return 'warning'
    case 1:
      return 'primary'
    case 2:
      return 'success'
    case 3:
      return 'info'
    default:
      return 'info'
  }
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(loadData)
</script>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.page-tip {
  margin-bottom: 16px;
}
.phone {
  font-family: var(--font-mono, monospace);
}
.copy-btn {
  margin-left: 6px;
}
.mono {
  font-family: var(--font-mono, monospace);
  color: var(--text-secondary, #606266);
}
.muted {
  color: var(--text-tertiary, #909399);
  font-size: 12px;
}
/* 已发码的行：过期时间在上，重发/作废在下 */
.sent-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.sent-actions {
  display: flex;
  gap: 6px;
}
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
.code-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-secondary, #606266);
}
.code-box {
  font-family: var(--font-mono, monospace);
  font-size: 40px;
  font-weight: 600;
  letter-spacing: 0.3em;
  text-align: center;
  padding: 18px 0;
  border-radius: 10px;
  background: var(--paper-sunken, #f5f5f3);
  color: var(--amber-700, #a96a25);
}
.code-expire {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-tertiary, #909399);
}
</style>
