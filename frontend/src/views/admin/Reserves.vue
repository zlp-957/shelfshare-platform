<template>
  <el-card shadow="never">
    <template #header>
      <div class="header-row">
        <div class="header-left">
          <el-input v-model="keyword" placeholder="搜索账号/书名" clearable style="width: 220px" @keyup.enter="handleSearch" />
          <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px; margin-left: 8px" @change="handleSearch">
            <el-option label="排队中" :value="0" />
            <el-option label="待借阅" :value="1" />
            <el-option label="已借阅" :value="2" />
            <el-option label="已取消" :value="3" />
            <el-option label="已失效" :value="4" />
          </el-select>
          <el-button type="primary" @click="handleSearch" style="margin-left: 8px">搜索</el-button>
        </div>
        <el-button type="warning" @click="handleProcessExpired">手动处理失效预约</el-button>
      </div>
    </template>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="userAccount" label="读者账号" width="130" />
      <el-table-column prop="userName" label="读者姓名" width="110" />
      <el-table-column prop="bookName" label="书名" min-width="150" />
      <el-table-column label="预约时间" width="170">
        <template #default="{ row }">{{ formatTime(row.reserveTime) }}</template>
      </el-table-column>
      <el-table-column label="排队位次" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">第 {{ row.queueNo }} 位</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning">排队中</el-tag>
          <el-tag v-else-if="row.status === 1" type="success">待借阅</el-tag>
          <el-tag v-else-if="row.status === 2" type="primary">已借阅</el-tag>
          <el-tag v-else-if="row.status === 3" type="info">已取消</el-tag>
          <el-tag v-else type="danger">已失效</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="有效截止" width="170">
        <template #default="{ row }">{{ row.validDeadline ? formatTime(row.validDeadline) : '—' }}</template>
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
import { pageReserves, processExpired } from '../../api'

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
    const res = await pageReserves({
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

const handleProcessExpired = async () => {
  await processExpired()
  ElMessage.success('处理完成')
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
</style>
