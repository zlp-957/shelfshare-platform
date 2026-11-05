<template>
  <el-card shadow="never">
    <template #header>
      <div class="header-row">
        <div class="header-left">
          <el-input
            v-model="keyword"
            placeholder="搜索账号 / 姓名 / 手机号"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch" style="margin-left: 8px">搜索</el-button>
        </div>
      </div>
    </template>

    <!-- 说明：读者账号由系统在注册时统一分配，资料由本人维护，管理员只做查看与账号管控 -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="page-tip"
      title="读者账号由系统统一分配，姓名与手机号由读者在个人中心自行维护。管理员可在此查看并禁用异常账号。"
    />

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="account" label="账号" width="140" />
      <el-table-column prop="name" label="姓名" width="140" />
      <el-table-column label="手机号" width="150">
        <template #default="{ row }">
          <span v-if="row.phone" class="phone">{{ row.phone }}</span>
          <span v-else class="muted">未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button :type="row.status === 0 ? 'danger' : 'success'" size="small" @click="handleToggle(row)">
            {{ row.status === 0 ? '禁用' : '启用' }}
          </el-button>
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
import { ElMessage } from 'element-plus'
import { pageReaders, toggleReader } from '../../api'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res = await pageReaders({
      page: page.value,
      size: size.value,
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

// 只保留账号状态管控：禁用后该账号无法登录，已登录的令牌也会失效
const handleToggle = async (row) => {
  await toggleReader(row.id)
  ElMessage.success('操作成功')
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
.page-tip {
  margin-bottom: 16px;
}
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
.phone {
  font-family: var(--font-mono, monospace);
  color: var(--text);
}
.muted {
  color: var(--text-tertiary, #909399);
}
</style>
