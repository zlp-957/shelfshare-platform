<template>
  <div>
    <header class="page-title">
      <h2>数据统计</h2>
      <span class="hint">馆藏、借阅与交易的实时概览</span>
    </header>

    <template v-if="stat">
      <section v-for="group in groups" :key="group.title" class="group">
        <div class="group-head">
          <span class="group-index">{{ group.index }}</span>
          <h3>{{ group.title }}</h3>
          <span class="group-rule"></span>
        </div>

        <div class="stat-grid">
          <article
            v-for="card in group.cards"
            :key="card.title"
            class="stat"
            :class="{ 'stat--alert': card.alert && card.raw > 0 }"
          >
            <div class="stat-top">
              <span class="stat-label">{{ card.title }}</span>
              <span class="stat-icon">
                <el-icon :size="16"><component :is="card.icon" /></el-icon>
              </span>
            </div>
            <div class="stat-value num">{{ card.value }}</div>
            <div class="stat-note">
              <span v-if="card.alert && card.raw > 0" class="alert-dot"></span>
              {{ card.note }}
            </div>
          </article>
        </div>
      </section>
    </template>

    <!-- 加载态：与真实卡片同尺寸，避免布局跳动 -->
    <div v-else class="stat-grid">
      <div v-for="n in 4" :key="n" class="stat stat--ghost"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getStat } from '../../api'

const stat = ref(null)

const count = (v) => Number(v ?? 0).toLocaleString('zh-CN')
const money = (v) => Number(v ?? 0).toFixed(2)

const groups = computed(() => {
  if (!stat.value) return []
  const s = stat.value
  return [
    {
      index: '01',
      title: '馆藏与读者',
      cards: [
        { title: '书目总数', value: count(s.bookCount), raw: s.bookCount, note: '不同的书名数', icon: 'Notebook' },
        { title: '馆藏副本', value: count(s.itemCount), raw: s.itemCount, note: '实体书册数', icon: 'Files' },
        { title: '在馆可借', value: count(s.availableItemCount), raw: s.availableItemCount, note: '当前可被借走的册数', icon: 'Collection' },
        { title: '读者总数', value: count(s.readerCount), raw: s.readerCount, note: '已注册读者', icon: 'User' }
      ]
    },
    {
      index: '02',
      title: '借阅流转',
      cards: [
        { title: '借阅中', value: count(s.borrowingCount), raw: s.borrowingCount, note: '已核销、尚未归还', icon: 'Tickets' },
        { title: '待取书', value: count(s.pendingPickupCount), raw: s.pendingPickupCount, note: '已付押金，等待到馆核销', icon: 'Clock', alert: true },
        { title: '已买断', value: count(s.boughtOutCount), raw: s.boughtOutCount, note: '逾期未还，押金已没收', icon: 'Warning' },
        { title: '预约排队中', value: count(s.queuingReserveCount), raw: s.queuingReserveCount, note: '等待书归还', icon: 'Timer' }
      ]
    },
    {
      index: '03',
      title: '交易',
      cards: [
        { title: '购买订单', value: count(s.purchaseCount), raw: s.purchaseCount, note: '已支付的购买单', icon: 'ShoppingCart' },
        { title: '购买总额', value: '¥' + money(s.totalPurchaseAmount), raw: s.totalPurchaseAmount, note: '累计成交金额', icon: 'Money' },
        { title: '在借押金', value: '¥' + money(s.depositInUse), raw: s.depositInUse, note: '待取书与借阅中占用的担保金', icon: 'Wallet' }
      ]
    }
  ]
})

onMounted(async () => {
  const res = await getStat()
  stat.value = res.data
})
</script>

<style scoped>
.group + .group {
  margin-top: var(--sp-7);
}

/* 分组标题：序号 + 标题 + 发丝线，工业风的层级手段 */
.group-head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}
.group-index {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--amber-700);
  letter-spacing: 0.08em;
}
.group-head h3 {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  white-space: nowrap;
}
.group-rule {
  flex: 1;
  height: 1px;
  background: var(--line);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--sp-4);
}

/* ---------------- 统计卡 ---------------- */
.stat {
  position: relative;
  padding: var(--sp-4) var(--sp-5) var(--sp-4);
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  transition: border-color var(--dur-base) ease, transform var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out);
}
.stat:hover {
  border-color: var(--line-strong);
  transform: translateY(-2px);
  box-shadow: var(--shadow-2);
}
/* 悬停时左侧浮出琥珀压线 */
.stat::before {
  content: "";
  position: absolute;
  left: -1px;
  top: 14px;
  bottom: 14px;
  width: 2px;
  border-radius: 2px;
  background: var(--amber-600);
  transform: scaleY(0);
  transform-origin: top;
  transition: transform var(--dur-base) var(--ease-out);
}
.stat:hover::before {
  transform: scaleY(1);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}
.stat-label {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  letter-spacing: 0.03em;
}
.stat-icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--r-sm);
  background: var(--green-50);
  color: var(--green-700);
}

.stat-value {
  margin-top: var(--sp-3);
  font-size: var(--fs-2xl);
  font-weight: 500;
  line-height: 1.1;
  color: var(--text);
  letter-spacing: -0.02em;
}
.stat-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

/* 需要管理员处理的项目：琥珀提示，不只靠颜色也要有圆点 */
.stat--alert {
  border-color: rgba(200, 134, 60, 0.4);
  background: linear-gradient(180deg, var(--amber-100) 0%, var(--paper-raised) 46%);
}
.stat--alert .stat-value {
  color: var(--amber-700);
}
.stat--alert .stat-icon {
  background: rgba(200, 134, 60, 0.16);
  color: var(--amber-700);
}
.alert-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--amber-600);
  flex-shrink: 0;
}

/* 加载占位 */
.stat--ghost {
  height: 116px;
  background: var(--paper-sunken);
  border-style: dashed;
  animation: pulse 1.4s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

/* 入场编排：按分组顺序错峰 */
@media (prefers-reduced-motion: no-preference) {
  .group {
    animation: rise 520ms var(--ease-out) both;
  }
  .group:nth-child(2) {
    animation-delay: 70ms;
  }
  .group:nth-child(3) {
    animation-delay: 140ms;
  }
  .group:nth-child(4) {
    animation-delay: 210ms;
  }
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
