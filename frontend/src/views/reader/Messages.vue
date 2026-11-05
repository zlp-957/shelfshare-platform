<template>
  <div class="msg-page">
    <el-card shadow="never">
      <template #header>
        <div class="header-row">
          <div class="header-left">
            <el-radio-group v-model="filter" size="default" @change="loadData">
              <el-radio-button :label="null">全部</el-radio-button>
              <el-radio-button :label="0">未读</el-radio-button>
            </el-radio-group>
            <span v-if="unread > 0" class="unread-tip">{{ unread }} 条未读</span>
          </div>
          <el-button text :disabled="unread === 0" @click="handleReadAll">全部标记已读</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && list.length === 0" description="暂无消息" />

      <div v-else v-loading="loading" class="msg-list">
        <div
          v-for="msg in list"
          :key="msg.id"
          class="msg-item"
          :class="{ unread: msg.isRead === 0 }"
          @click="handleRead(msg)"
        >
          <div class="msg-icon">
            <el-icon :size="17">
              <Warning v-if="msg.type === 2" />
              <AlarmClock v-else-if="msg.type === 1" />
              <Timer v-else-if="msg.type === 3" />
              <Bell v-else />
            </el-icon>
          </div>
          <div class="msg-body">
            <div class="msg-head">
              <span class="msg-title">{{ msg.title }}</span>
              <el-tag size="small" :type="tagType(msg.type)" effect="plain">
                {{ typeText(msg.type) }}
              </el-tag>
              <span class="msg-time">{{ formatTime(msg.createTime) }}</span>
            </div>
            <p class="msg-content">{{ msg.content }}</p>
          </div>
          <span v-if="msg.isRead === 0" class="dot"></span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { myMessages, readMessage, readAllMessages, unreadMessageCount } from '../../api'

const list = ref([])
const loading = ref(false)
const filter = ref(null)
const unread = ref(0)

const loadData = async () => {
  loading.value = true
  try {
    const res = await myMessages({ isRead: filter.value === null ? undefined : filter.value })
    list.value = res.data || []
    unread.value = (await unreadMessageCount()).data || 0
  } finally {
    loading.value = false
  }
}

const handleRead = async (msg) => {
  if (msg.isRead === 1) return
  await readMessage(msg.id)
  msg.isRead = 1
  unread.value = Math.max(unread.value - 1, 0)
}

const handleReadAll = async () => {
  await readAllMessages()
  ElMessage.success('已全部标记为已读')
  loadData()
}

const typeText = (type) => {
  if (type === 1) return '到期提醒'
  if (type === 2) return '逾期提醒'
  if (type === 3) return '预约到书'
  return '系统通知'
}

const tagType = (type) => {
  if (type === 2) return 'danger'
  if (type === 1) return 'warning'
  if (type === 3) return 'success'
  return 'info'
}

const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

onMounted(loadData)
</script>

<style scoped>
.msg-page {
  max-width: 920px;
}
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.unread-tip {
  font-size: var(--fs-sm);
  color: var(--amber-600, #a06b30);
}
.msg-list {
  display: flex;
  flex-direction: column;
}
.msg-item {
  position: relative;
  display: flex;
  gap: 14px;
  padding: 16px 8px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
  transition: background-color 0.18s ease;
}
.msg-item:last-child {
  border-bottom: none;
}
.msg-item:hover {
  background: var(--paper-sunken);
}
.msg-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--paper-sunken);
  color: var(--text-secondary);
}
.msg-item.unread .msg-icon {
  background: rgba(200, 134, 60, 0.12);
  color: var(--amber-600, #a06b30);
}
.msg-body {
  flex: 1;
  min-width: 0;
}
.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.msg-title {
  font-size: var(--fs-base);
  color: var(--text-muted);
  font-weight: 400;
}
.msg-item.unread .msg-title {
  color: var(--text);
  font-weight: 600;
}
.msg-time {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.msg-content {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--text-secondary);
}
.dot {
  position: absolute;
  right: 2px;
  top: 22px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-danger);
}
</style>
