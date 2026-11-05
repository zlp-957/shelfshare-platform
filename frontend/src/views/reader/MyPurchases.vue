<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>我的购买</span>
        <span class="count">{{ list.length }} 笔</span>
      </div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="page-tip"
      title="付款后订单进入「待取书」，到馆用上方取书码由工作人员核销。核销前不想买了可以取消并全额退款；核销后交易完成，不再支持退款。"
    />

    <PickupCodeCard />

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="orderNo" label="订单编号" min-width="180" />
      <el-table-column prop="bookName" label="书名" min-width="150">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/reader/book/${row.bookId}`)">{{ row.bookName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">
          <span class="num price">¥{{ money(row.price) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row)" effect="light">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.cancelable"
            type="danger"
            size="small"
            plain
            :loading="canceling === row.id"
            @click="handleCancel(row)"
          >
            取消订单
          </el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && list.length === 0" description="还没有购买记录" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myOrders, cancelPurchaseOrder } from '../../api'
import PickupCodeCard from '../../components/PickupCodeCard.vue'

const list = ref([])
const loading = ref(false)
const canceling = ref(null)

const loadData = async () => {
  loading.value = true
  try {
    const res = await myOrders()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消《${row.bookName}》的这笔购买吗？\n书款 ¥${money(row.price)} 将原路退回，书会释放回馆藏。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消并退款' }
    )
  } catch (e) {
    return
  }
  canceling.value = row.id
  try {
    await cancelPurchaseOrder(row.id)
    ElMessage.success('订单已取消，书款将原路退回')
    loadData()
  } catch (e) {
    ElMessage.error(e?.message || '取消失败')
  } finally {
    canceling.value = null
  }
}

function statusTagType(row) {
  if (row.status === 0) return 'info'      // 待支付
  if (row.status === 2) return 'info'      // 已取消
  return row.pickupStatus === 1 ? 'success' : 'warning'  // 已取书 / 待取书
}

const money = (v) => Number(v || 0).toFixed(2)

const formatTime = (time) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

onMounted(loadData)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.count {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  font-weight: 400;
  color: var(--text-muted);
}
.page-tip {
  margin-bottom: 16px;
}
.num {
  font-family: var(--font-mono, monospace);
  font-variant-numeric: tabular-nums;
}
.price {
  color: var(--amber-700);
  font-weight: 500;
}
.mono {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-secondary, #606266);
}
.muted {
  color: var(--text-tertiary, #909399);
}
</style>
