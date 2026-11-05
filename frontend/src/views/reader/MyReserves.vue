<template>
  <el-card shadow="never">
    <template #header>
      <span>我的预约</span>
    </template>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="bookName" label="书名" min-width="150">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/reader/book/${row.bookId}`)">{{ row.bookName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="预约时间" width="180">
        <template #default="{ row }">{{ formatTime(row.reserveTime) }}</template>
      </el-table-column>
      <el-table-column label="排队位次" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">第 {{ row.queueNo }} 位</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">排队中</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">待借阅</el-tag>
          <el-tag v-else-if="row.status === 2" type="primary">已借阅</el-tag>
          <el-tag v-else-if="row.status === 3" type="info">已取消</el-tag>
          <el-tag v-else type="danger">已失效</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="有效截止" width="180">
        <template #default="{ row }">
          <span v-if="row.validDeadline" :style="row.status === 1 ? 'color:var(--amber-700);font-weight:500' : ''">{{ formatTime(row.validDeadline) }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0 || row.status === 1" type="danger" size="small" @click="handleCancel(row)">取消</el-button>
          <el-button v-if="row.status === 1" type="primary" size="small" @click="$router.push(`/reader/book/${row.bookId}`)">去借阅</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && list.length === 0" description="暂无预约记录" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myReserves, cancelReserve } from '../../api'

const list = ref([])
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const res = await myReserves()
    list.value = res.data
  } finally {
    loading.value = false
  }
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(`确定取消预约《${row.bookName}》吗？`, '取消确认', { type: 'warning' })
  } catch (e) {
    return
  }
  await cancelReserve(row.id)
  ElMessage.success('取消成功')
  loadData()
}

const formatTime = (time) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

onMounted(loadData)
</script>
