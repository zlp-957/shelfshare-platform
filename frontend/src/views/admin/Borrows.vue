<template>
  <el-card shadow="never">
    <template #header>
      <div class="header-row">
        <div class="header-left">
          <el-input v-model="keyword" placeholder="搜索账号/书名" clearable style="width: 220px" @keyup.enter="handleSearch" />
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px; margin-left: 8px" @change="handleSearch">
            <el-option label="待取书" :value="0" />
            <el-option label="借阅中" :value="1" />
            <el-option label="已归还" :value="2" />
            <el-option label="已买断" :value="3" />
            <el-option label="已取消" :value="4" />
          </el-select>
          <el-button type="primary" @click="handleSearch" style="margin-left: 8px">搜索</el-button>
        </div>
        <el-button type="warning" @click="handleProcessOverdue">手动触发逾期处理</el-button>
      </div>
    </template>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="userAccount" label="读者账号" width="130" />
      <el-table-column prop="userName" label="读者姓名" width="110" />
      <el-table-column label="书名" prop="bookName" min-width="150" />
      <el-table-column label="副本条码" width="110">
        <template #default="{ row }">
          <span class="mono">{{ row.barcode || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="押金" width="100">
        <template #default="{ row }">￥{{ Number(row.depositAmount || 0).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="借阅时间" width="170">
        <template #default="{ row }">{{ formatTime(row.borrowTime) }}</template>
      </el-table-column>
      <el-table-column label="应还时间" width="170">
        <template #default="{ row }">{{ formatTime(row.deadlineTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">待取书</el-tag>
          <el-tag v-else-if="row.status === 1" type="primary">借阅中</el-tag>
          <el-tag v-else-if="row.status === 2" type="success">已归还</el-tag>
          <el-tag v-else-if="row.status === 3" type="danger">已买断</el-tag>
          <el-tag v-else type="info">已取消</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination">
      <el-pagination background layout="prev, pager, next, total" :total="total" :page-size="size" :current-page="page" @current-change="handlePageChange" />
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { pageBorrows, processOverdue } from '../../api'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const status = ref(null)

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageBorrows({
      page: page.value, size: size.value,
      keyword: keyword.value || undefined, status: status.value === null ? undefined : status.value
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

const handleProcessOverdue = async () => {
  await processOverdue()
  ElMessage.success('逾期处理完成')
  loadData()
}

const formatTime = (time) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

onMounted(loadData)
</script>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
}
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
.mono {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-secondary, #606266);
}
</style>
