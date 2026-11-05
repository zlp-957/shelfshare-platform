<template>
  <el-container class="layout">
    <el-aside width="238px" class="aside">
      <div class="spine">
        <div class="aside-logo">
          <div class="logo-mark">
            <el-icon :size="18"><Reading /></el-icon>
          </div>
          <div class="logo-text">
            <span class="logo-cn">书屿</span>
            <span class="logo-en">SHELFSHARE · ADMIN</span>
          </div>
        </div>

        <el-menu :default-active="activeMenu" router class="nav">
          <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>

        <div class="aside-foot">
          <span class="dot"></span>
          <span>系统运行中</span>
        </div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="crumb">管理后台</span>
          <span class="crumb-sep">/</span>
          <span class="crumb-current">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand" trigger="click">
            <span class="user-info">
              <el-avatar
                :size="32"
                class="avatar"
                :src="userStore.userInfo?.avatar || undefined"
              >
                {{ userStore.userInfo?.avatar ? '' : (userStore.userInfo?.name?.charAt(0) || 'A') }}
              </el-avatar>
              <span class="user-text">
                <span class="username">{{ userStore.userInfo?.name }}</span>
                <span class="user-role">管理员</span>
              </span>
              <el-icon class="chev"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="reader">前往读者端</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <div class="page-shell">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import { getUserInfo } from '../api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 登录返回里没有头像，刷新后从 localStorage 恢复会丢，这里补拉一次
onMounted(async () => {
  try {
    const res = await getUserInfo()
    userStore.patchUserInfo({
      avatar: res.data.avatar,
      name: res.data.name
    })
  } catch (e) {
    // 静默失败，用本地缓存
  }
})

// 菜单顺序按使用频率和业务主线排：概览 → 日常借还 → 读者与账号 → 内容与配置
// 前 5 项是每天都会点到的，配置类放在后面，避免把高频入口埋在中间
const menus = [
  { path: '/admin', label: '数据统计', icon: 'DataAnalysis' },
  { path: '/admin/checkout', label: '取书与归还', icon: 'Finished' },
  { path: '/admin/borrows', label: '借阅管理', icon: 'Tickets' },
  { path: '/admin/orders', label: '订单管理', icon: 'ShoppingCart' },
  { path: '/admin/reserves', label: '预约管理', icon: 'Timer' },
  { path: '/admin/users', label: '用户管理', icon: 'User' },
  { path: '/admin/reconcile', label: '资金对账', icon: 'Money' },
  { path: '/admin/operation-logs', label: '操作日志', icon: 'Document' },
  { path: '/admin/password-resets', label: '密码重置', icon: 'Key' },
  { path: '/admin/books', label: '藏书管理', icon: 'Notebook' },
  { path: '/admin/types', label: '分类管理', icon: 'Collection' },
  { path: '/admin/notices', label: '公告管理', icon: 'Bell' },
  { path: '/admin/profile', label: '个人中心', icon: 'Avatar' }
]

const activeMenu = computed(() => route.path)

// 顶栏面包屑末级标题，与侧栏菜单共用同一份数据
const pageTitle = computed(() => {
  const hit = menus.find((m) => m.path === route.path)
  return hit ? hit.label : '数据统计'
})

const handleCommand = async (command) => {
  if (command === 'reader') {
    router.push('/reader')
  } else if (command === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

/* ---------------- 侧栏：书脊形态 ---------------- */
.aside {
  background: var(--ink-800);
  overflow: hidden;
}
.spine {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
}
/* 纵向书脊发丝线 */
.spine::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    90deg,
    rgba(244, 242, 237, 0.04) 0 1px,
    transparent 1px 34px
  );
  pointer-events: none;
}
/* 右缘琥珀描边，作为整栏的“书脊压线” */
.spine::after {
  content: "";
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(
    180deg,
    transparent,
    rgba(200, 134, 60, 0.55) 18%,
    rgba(200, 134, 60, 0.55) 82%,
    transparent
  );
  pointer-events: none;
}

.aside-logo {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 68px;
  padding: 0 var(--sp-5);
  border-bottom: 1px solid rgba(244, 242, 237, 0.1);
}
.logo-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--r-md);
  background: rgba(200, 134, 60, 0.16);
  border: 1px solid rgba(200, 134, 60, 0.38);
  color: var(--amber-500);
}
.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.logo-cn {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text-inverse);
  letter-spacing: 0.06em;
}
.logo-en {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  color: rgba(244, 242, 237, 0.42);
  margin-top: 2px;
}

/* ---------------- 导航 ---------------- */
.nav {
  position: relative;
  flex: 1;
  padding: var(--sp-4) var(--sp-3);
  border-right: none;
  background: transparent;
  overflow-y: auto;
}
.nav :deep(.el-menu-item) {
  position: relative;
  height: 44px;
  line-height: 44px;
  margin-bottom: 2px;
  padding-left: 14px !important;
  border-radius: var(--r-md);
  color: rgba(244, 242, 237, 0.7);
  font-size: var(--fs-base);
  letter-spacing: 0.03em;
  transition: background-color var(--dur-fast) ease, color var(--dur-fast) ease,
    padding-left var(--dur-fast) var(--ease-out);
}
.nav :deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 17px;
  color: rgba(244, 242, 237, 0.5);
  transition: color var(--dur-fast) ease;
}
.nav :deep(.el-menu-item:hover) {
  background: rgba(244, 242, 237, 0.07);
  color: var(--text-inverse);
  padding-left: 18px !important;
}
.nav :deep(.el-menu-item:hover .el-icon) {
  color: rgba(244, 242, 237, 0.8);
}
.nav :deep(.el-menu-item.is-active) {
  background: rgba(244, 242, 237, 0.11);
  color: #fff;
  font-weight: 500;
}
.nav :deep(.el-menu-item.is-active .el-icon) {
  color: var(--amber-500);
}
/* 激活态左侧琥珀指示条 */
.nav :deep(.el-menu-item.is-active)::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--amber-500);
}

.aside-foot {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: var(--sp-4) var(--sp-5);
  font-size: var(--fs-xs);
  letter-spacing: 0.06em;
  color: rgba(244, 242, 237, 0.42);
  border-top: 1px solid rgba(244, 242, 237, 0.08);
}
.aside-foot .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green-500);
  box-shadow: 0 0 0 3px rgba(77, 138, 123, 0.18);
}

/* ---------------- 顶栏 ---------------- */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 var(--sp-6);
  background: var(--paper-raised);
  border-bottom: 1px solid var(--line);
}
.header-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--fs-sm);
}
.crumb {
  color: var(--text-muted);
  letter-spacing: 0.06em;
}
.crumb-sep {
  color: var(--line-strong);
}
.crumb-current {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 12px 5px 5px;
  border-radius: var(--r-pill);
  cursor: pointer;
  transition: background-color var(--dur-fast) ease;
}
.user-info:hover {
  background: var(--paper-sunken);
}
.avatar {
  background: var(--green-700);
  font-family: var(--font-serif);
  font-weight: 600;
  font-size: 14px;
}
.user-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.username {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--text);
}
.user-role {
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.06em;
}
.chev {
  color: var(--text-muted);
  font-size: 12px;
}

/* ---------------- 主区 ---------------- */
.main {
  padding: var(--sp-5) var(--sp-6) var(--sp-7);
  background: var(--paper);
  overflow-y: auto;
}

@media (max-width: 900px) {
  .aside {
    display: none;
  }
  .header {
    padding: 0 var(--sp-4);
  }
  .main {
    padding: var(--sp-4);
  }
}
</style>
