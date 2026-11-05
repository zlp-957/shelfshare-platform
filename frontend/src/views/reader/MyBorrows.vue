<template>
  <div class="borrows-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的借阅</span>
          <el-radio-group v-model="status" @change="loadData">
            <el-radio-button :label="undefined">全部</el-radio-button>
            <el-radio-button :label="0">待取书</el-radio-button>
            <el-radio-button :label="1">借阅中</el-radio-button>
            <el-radio-button :label="2">已归还</el-radio-button>
            <el-radio-button :label="3">已买断</el-radio-button>
            <el-radio-button :label="4">已取消</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="page-tip"
        title="取书与归还都要到馆由工作人员核销。借阅与购买的取书统一用上方那个码，一个码可一次办完全部待取"
      />

      <PickupCodeCard />

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column label="封面" width="88">
          <template #default="{ row }">
            <el-image
              v-if="row.bookCover"
              :src="row.bookCover"
              fit="cover"
              style="width: 46px; height: 56px; border-radius: 4px"
            />
            <div v-else class="cover-blank">
              <el-icon><Notebook /></el-icon>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="bookName" label="书名" min-width="160" />

        <el-table-column label="副本条码" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.barcode || '待核销' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="押金" width="100">
          <template #default="{ row }">￥{{ money(row.depositAmount) }}</template>
        </el-table-column>

        <el-table-column label="下单时间" width="160">
          <template #default="{ row }">{{ formatTime(row.borrowTime) }}</template>
        </el-table-column>

        <el-table-column label="应还时间" width="160">
          <template #default="{ row }">
            <!-- 已取消的单不会再有核销，别提示「核销后开始计算」 -->
            <span v-if="row.status === 4" class="muted">—</span>
            <span v-else>{{ row.verifyTime ? formatTime(row.deadlineTime) : '核销后开始计算' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ row.statusText }}
            </el-tag>
            <div v-if="row.status === 1 && row.remainDays !== null && row.remainDays !== undefined" class="remain">
              <span :class="row.remainDays < 0 ? 'warn' : ''">
                {{ row.remainDays < 0 ? `已逾期 ${-row.remainDays} 天` : `剩余 ${row.remainDays} 天` }}
              </span>
            </div>
            <div v-else-if="row.status === 0 && row.payStatus === 0" class="remain">
              <span class="warn">待支付押金</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="押金结算" width="150">
          <template #default="{ row }">
            <!-- 已归还 / 已买断 / 已取消 都可能产生押金结算。
                 已取消以前被漏在判断外，导致「押金退了、页面上却显示 —」 -->
            <template v-if="row.status === 2 || row.status === 3 || row.status === 4">
              <div class="settle">
                <span v-if="Number(row.deductAmount) > 0" class="warn">
                  扣除 ￥{{ money(row.deductAmount) }}
                </span>
                <template v-if="Number(row.refundAmount) > 0">
                  <!-- 退款失败时钱还在路上，不能显示成已退，否则用户以为到账了 -->
                  <span v-if="row.refundStatus === 2" class="warn">
                    退款处理中 ￥{{ money(row.refundAmount) }}
                  </span>
                  <span v-else class="ok">退还 ￥{{ money(row.refundAmount) }}</span>
                </template>
                <span
                  v-if="Number(row.deductAmount) === 0 && Number(row.refundAmount) === 0"
                  class="muted"
                >
                  未产生押金
                </span>
              </div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="danger"
              size="small"
              plain
              @click="handleCancel(row)"
            >
              取消借阅
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无借阅记录" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myBorrows, cancelBorrow } from '../../api'
import PickupCodeCard from '../../components/PickupCodeCard.vue'

const list = ref([])
const loading = ref(false)
const status = ref(undefined)

async function loadData() {
  loading.value = true
  try {
    const res = await myBorrows(status.value)
    list.value = res.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
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
      return 'danger'
    case 4:
      return 'info'
    default:
      return 'info'
  }
}

async function handleCancel(row) {
  // 只有真实收过钱（已支付 1 / 部分退款 4）才谈得上退押金。
  // 未付款的单直接关单，提示「押金将退回」会让用户以为收过钱
  const paid = row.payStatus === 1 || row.payStatus === 4
  const tip = paid
    ? `确定取消《${row.bookName}》的借阅吗？押金 ￥${money(row.depositAmount)} 将原路退回。`
    : `确定取消《${row.bookName}》的借阅吗？该笔押金尚未支付，取消后不会产生退款。`
  try {
    await ElMessageBox.confirm(tip, '取消借阅', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await cancelBorrow(row.id)
    ElMessage.success(paid ? '已取消，押金将原路退回' : '已取消')
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '取消失败')
  }
}

function money(v) {
  return Number(v || 0).toFixed(2)
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(loadData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.page-tip {
  margin-bottom: 16px;
}

.cover-blank {
  width: 46px;
  height: 56px;
  border-radius: 4px;
  background: var(--paper-sunken, #f5f5f3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #b8bdb6;
}

.mono {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-secondary, #606266);
}

.pickup-code {
  font-family: var(--font-mono, monospace);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--amber-700);
}

.remain {
  font-size: 11px;
  margin-top: 2px;
  color: var(--text-tertiary, #909399);
}

.settle {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
}

.warn {
  color: #d85a30;
}

.ok {
  color: #1d9e75;
}

.muted {
  color: var(--text-tertiary, #909399);
}
</style>
