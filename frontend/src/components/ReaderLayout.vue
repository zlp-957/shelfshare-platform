<template>
  <el-container class="layout">
    <!-- 左侧导航：与管理端同一套书脊皮肤，两端视觉统一 -->
    <el-aside width="238px" class="aside">
      <div class="spine">
        <div class="aside-logo" @click="$router.push('/reader')">
          <div class="logo-mark">
            <el-icon :size="18"><Reading /></el-icon>
          </div>
          <div class="logo-text">
            <span class="logo-cn">书屿</span>
            <span class="logo-en">SHELFSHARE · READER</span>
          </div>
        </div>

        <el-menu :default-active="activeMenu" router class="nav">
          <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
            <el-badge
              v-if="item.badge === 'cart'"
              :value="cartStore.count"
              :hidden="cartStore.count === 0"
              :max="99"
              class="nav-badge"
            >
              <el-icon><component :is="item.icon" /></el-icon>
            </el-badge>
            <el-badge
              v-else-if="item.badge === 'message'"
              :value="unreadCount"
              :hidden="unreadCount === 0"
              :max="99"
              class="nav-badge"
            >
              <el-icon><component :is="item.icon" /></el-icon>
            </el-badge>
            <el-icon v-else><component :is="item.icon" /></el-icon>
            <span class="nav-label">{{ item.label }}</span>
          </el-menu-item>
        </el-menu>

        <div class="aside-foot">
          <span class="dot"></span>
          <span>读者端</span>
        </div>
      </div>
    </el-aside>

    <el-container class="right">
      <el-header class="header">
        <h1 class="page-title">{{ pageTitle }}</h1>
        <div class="header-right">
          <!-- 管理员从后台切过来看读者端时，给一条明确的回去的路 -->
          <el-button
            v-if="isAdmin"
            text
            class="admin-entry"
            @click="$router.push('/admin')"
          >
            <el-icon :size="15"><Back /></el-icon>
            <span>返回管理后台</span>
          </el-button>
          <el-dropdown @command="handleCommand" trigger="click">
            <span class="user-info">
              <el-avatar
                :size="32"
                class="avatar"
                :src="userStore.userInfo?.avatar || undefined"
              >
                {{ userStore.userInfo?.avatar ? '' : (userStore.userInfo?.name?.charAt(0) || 'U') }}
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.name }}</span>
              <el-icon class="chev"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- 个人中心已并入左侧导航，这里只留账号相关的操作 -->
                <el-dropdown-item v-if="isAdmin" command="admin">
                  <el-icon><Back /></el-icon>返回管理后台
                </el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import { useCartStore } from '../stores/cart'
import { getUserInfo, unreadMessageCount } from '../api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

// 登录接口的返回里没有头像，刷新后 store 从 localStorage 恢复就丢了。
// 这里补拉一次 /user/info，顺带让「改了头像/姓名后」的显示能立刻对上。
// 未读消息数：侧栏红点用。切页面时轻量刷一次，
// 不轮询——借阅提醒是分钟级时效，不是秒级，没必要为它常驻一个定时器
const unreadCount = ref(0)
const refreshUnread = async () => {
  try {
    unreadCount.value = (await unreadMessageCount()).data || 0
  } catch (e) {
    unreadCount.value = 0
  }
}

onMounted(async () => {
  try {
    const res = await getUserInfo()
    userStore.patchUserInfo({
      avatar: res.data.avatar,
      name: res.data.name
    })
  } catch (e) {
    // 拉不到就用本地缓存，不打扰用户
  }
  refreshUnread()
})

watch(() => route.path, refreshUnread)

// 管理员也拥有读者身份，可以来这里看读者端效果；给他留一条回后台的路
const isAdmin = computed(() => userStore.isAdmin)

// 侧栏菜单：把「购物车」「个人中心」也收进来，顶栏只留账号相关操作。
// 管理员不需要这里的个人中心（他的在管理端），点过去也会被路由守卫送回去，索性不显示。
const baseMenus = [
  { path: '/reader', label: '图书检索', icon: 'Search' },
  { path: '/reader/cart', label: '购物车', icon: 'ShoppingCart', badge: 'cart' },
  { path: '/reader/borrows', label: '我的借阅', icon: 'Tickets' },
  { path: '/reader/reserves', label: '我的预约', icon: 'Timer' },
  { path: '/reader/purchases', label: '我的购买', icon: 'ShoppingBag' },
  { path: '/reader/messages', label: '消息中心', icon: 'ChatDotRound', badge: 'message' }
]

const menus = computed(() => {
  if (isAdmin.value) return baseMenus
  return [...baseMenus, { path: '/reader/profile', label: '个人中心', icon: 'User' }]
})

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/reader/book')) return '/reader'
  return path
})

// 顶栏标题跟着侧栏选中项走，避免顶栏空着
const pageTitle = computed(() => {
  const hit = menus.value.find((m) => m.path === activeMenu.value)
  return hit ? hit.label : '图书检索'
})

// 余额统一从全局 store 读取，购买/借阅后更新 store 即可即时刷新
const handleCommand = async (command) => {
  if (command === 'admin') {
    router.push('/admin')
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
  background: var(--paper);
}

/* ---------------- 侧栏：与管理端同一套书脊形态 ---------------- */
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
  cursor: pointer;
  flex-shrink: 0;
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
  flex-shrink: 0;
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

/* 购物车角标：深色底上要收小、去掉白边，否则会压住图标 */
.nav-badge {
  display: inline-flex;
  margin-right: 10px;
}
.nav-badge :deep(.el-icon) {
  margin-right: 0 !important;
  color: inherit;
}
.nav-badge :deep(.el-badge__content) {
  height: 15px;
  min-width: 15px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 15px;
  top: 4px;
  right: 8px;
  border: 1px solid var(--ink-800);
  box-shadow: none;
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

/* ---------------- 右侧主区 ---------------- */
.right {
  background: var(--paper);
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 68px;
  padding: 0 var(--sp-6);
  background: rgba(251, 250, 247, 0.86);
  backdrop-filter: saturate(150%) blur(10px);
  -webkit-backdrop-filter: saturate(150%) blur(10px);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 20;
}
.page-title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: var(--fs-lg);
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-shrink: 0;
}

/* 管理员返回入口：低调但明确，不抢读者端的视觉重心 */
.admin-entry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}
.admin-entry:hover {
  color: var(--text);
  background: var(--paper-sunken);
  border-color: var(--line-strong);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px 5px 5px;
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
.username {
  font-size: var(--fs-sm);
  color: var(--text);
}
.chev {
  color: var(--text-muted);
  font-size: 12px;
}

.main {
  padding: var(--sp-5) var(--sp-6) var(--sp-7);
  background: var(--paper);
  overflow-y: auto;
}

@media (max-width: 1080px) {
  /* 窄屏侧栏收成图标条，文字全部隐藏 */
  .aside {
    width: 64px !important;
  }
  .aside-logo {
    padding: 0;
    justify-content: center;
  }
  .logo-text,
  .aside-foot span,
  .nav-label {
    display: none;
  }
  .nav {
    padding: var(--sp-4) 8px;
  }
  .nav :deep(.el-menu-item) {
    justify-content: center;
    padding-left: 0 !important;
    padding-right: 0 !important;
  }
  .nav :deep(.el-menu-item:hover) {
    padding-left: 0 !important;
  }
  .nav :deep(.el-menu-item .el-icon) {
    margin-right: 0;
  }
  .nav-badge {
    margin-right: 0;
  }
  .aside-foot {
    justify-content: center;
    padding: var(--sp-4) 0;
  }
  .header {
    padding: 0 var(--sp-4);
  }
  .page-title {
    font-size: var(--fs-base);
  }
  .username {
    display: none;
  }
  .admin-entry span {
    display: none;
  }
  .admin-entry {
    padding: 0 8px;
  }
  .main {
    padding: var(--sp-4);
  }
}
</style>
