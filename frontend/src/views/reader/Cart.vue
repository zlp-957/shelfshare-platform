<template>
  <div class="cart-page">
    <header class="page-title">
      <h2>购物车</h2>
      <span class="hint">加购的书都在这里，结算时再决定借还是买</span>
    </header>

    <!-- 空态 -->
    <div v-if="cartStore.count === 0" class="empty">
      <div class="empty-mark">
        <el-icon :size="26"><ShoppingCart /></el-icon>
      </div>
      <h3>购物车还是空的</h3>
      <p>去书架挑几本喜欢的书，借阅和购买都可以先放进来</p>
      <el-button type="primary" @click="$router.push('/reader')">去逛书架</el-button>
    </div>

    <template v-else>
      <!-- 结算方式 -->
      <section class="mode">
        <div class="mode-head">
          <span class="mode-label">结算方式</span>
          <div class="seg">
            <button type="button" :class="{ on: mode === 'borrow' }" @click="mode = 'borrow'">
              <el-icon><Tickets /></el-icon>借阅
            </button>
            <button type="button" :class="{ on: mode === 'buy' }" @click="mode = 'buy'">
              <el-icon><ShoppingCart /></el-icon>购买
            </button>
          </div>
        </div>
        <p class="mode-hint">{{ modeHint }}</p>
      </section>

      <!-- 清单 -->
      <section class="list">
        <div class="list-head">
          <el-checkbox
            :model-value="allPicked"
            :indeterminate="selectedIds.length > 0 && !allPicked"
            @change="toggleAll"
          >
            全选
          </el-checkbox>
          <span class="count">共 {{ cartStore.count }} 本</span>
        </div>

        <article
          v-for="row in cartStore.items"
          :key="row.bookId"
          class="item"
          :class="{ picked: selectedIds.includes(row.bookId) }"
          @click="toggle(row.bookId)"
        >
          <el-checkbox
            :model-value="selectedIds.includes(row.bookId)"
            @click.stop
            @change="toggle(row.bookId)"
          />

          <div class="cover">
            <el-image v-if="row.cover" :src="row.cover" fit="cover" class="cover-img" />
            <div v-else class="cover-blank">
              <el-icon :size="20"><Notebook /></el-icon>
            </div>
          </div>

          <div class="meta">
            <h3 :title="row.name">{{ row.name }}</h3>
            <p>{{ row.author || '佚名' }}</p>
          </div>

          <div class="amount">
            <span class="num price">¥{{ money(row.price) }}</span>
            <em>{{ mode === 'borrow' ? '押金' : '书款' }}</em>
          </div>

          <button type="button" class="del" title="移出购物车" @click.stop="remove(row.bookId)">
            <el-icon><Close /></el-icon>
          </button>
        </article>
      </section>

      <!-- 吸底结算栏 -->
      <div class="settle">
        <div class="settle-info">
          <p class="picked-line">
            已选 <b class="num">{{ selected.length }}</b> 本
            <span v-if="selected.length < cartStore.count" class="rest">
              其余 {{ cartStore.count - selected.length }} 本留在购物车
            </span>
          </p>
          <p class="total-line">
            <span class="k">{{ mode === 'borrow' ? '押金合计' : '书款合计' }}</span>
            <b class="num">¥{{ money(selectedTotal) }}</b>
          </p>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="selected.length === 0"
          @click="checkout"
        >
          去结算
        </el-button>
      </div>
    </template>

    <AgreementDialog v-model="agreementVisible" :type="agreementType" @agreed="doCheckout" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { cartCheckout } from '../../api'
import { submitPayForm } from '../../utils/pay'
import AgreementDialog from '../../components/AgreementDialog.vue'
import { useCartStore } from '../../stores/cart'

const cartStore = useCartStore()
const submitting = ref(false)

/** 本次结算方式：borrow 借阅 / buy 购买 */
const mode = ref('borrow')
/** 当前勾选的图书 id */
const selectedIds = ref([])

const agreementVisible = ref(false)
const agreementType = ref(1)

const selected = computed(() =>
  cartStore.items.filter((i) => selectedIds.value.includes(i.bookId))
)

const allPicked = computed(
  () => cartStore.items.length > 0 && selectedIds.value.length === cartStore.items.length
)

const selectedTotal = computed(() =>
  selected.value.reduce((s, r) => s + Number(r.price || 0), 0)
)

const modeHint = computed(() =>
  mode.value === 'borrow'
    ? '按借阅结算：缴纳与书价等额的押金，按期归还后全额退回'
    : '按购买结算：支付书款，书归你所有，不退不换'
)

function money(v) {
  return Number(v || 0).toFixed(2)
}

function toggle(bookId) {
  const i = selectedIds.value.indexOf(bookId)
  if (i > -1) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(bookId)
}

function toggleAll(val) {
  selectedIds.value = val ? cartStore.items.map((i) => i.bookId) : []
}

// 加进购物车的都是用户想处理的，默认全选，可手动取消
onMounted(() => {
  if (cartStore.count > 0) {
    selectedIds.value = cartStore.items.map((i) => i.bookId)
  }
})

function remove(bookId) {
  cartStore.remove(bookId)
  selectedIds.value = selectedIds.value.filter((id) => id !== bookId)
}

function checkout() {
  if (selected.value.length === 0) {
    ElMessage.warning('请先勾选要结算的图书')
    return
  }
  // 一次结算只有一种方式，所以只确认对应那一条规则
  agreementType.value = mode.value === 'borrow' ? 1 : 2
  agreementVisible.value = true
}

async function doCheckout() {
  submitting.value = true
  try {
    const ids = selected.value.map((r) => r.bookId)
    const items = ids.map((bookId) => ({ bookId, mode: mode.value }))
    const res = await cartCheckout(items)
    ElMessage.success('订单已生成，正在跳转支付')
    // 只清掉已结算的，没勾选的书继续留在购物车
    cartStore.removeMany(ids)
    selectedIds.value = []
    await submitPayForm(res.data.payForm, res.data.orderNo)
  } catch (e) {
    ElMessage.error(e?.message || '结算失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.cart-page {
  max-width: 960px;
  margin: 0 auto;
}

/* ---------------- 空态 ---------------- */
.empty {
  padding: var(--sp-8) var(--sp-5);
  text-align: center;
  background: var(--paper-raised);
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-xl);
}
.empty-mark {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin: 0 auto var(--sp-4);
  border-radius: 50%;
  background: var(--paper-sunken);
  color: var(--text-muted);
}
.empty h3 {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  margin-bottom: var(--sp-2);
}
.empty p {
  font-size: var(--fs-sm);
  color: var(--text-muted);
  margin-bottom: var(--sp-5);
}

/* ---------------- 结算方式 ---------------- */
.mode {
  padding: var(--sp-4) var(--sp-5);
  margin-bottom: var(--sp-4);
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}
.mode-head {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}
.mode-label {
  font-size: var(--fs-sm);
  letter-spacing: 0.06em;
  color: var(--text-muted);
}

/* 分段控件：两格紧贴，选中态填充主色 */
.seg {
  display: inline-flex;
  padding: 3px;
  border-radius: var(--r-md);
  background: var(--paper-sunken);
  border: 1px solid var(--line);
}
.seg button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 20px;
  border: none;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-family: inherit;
  cursor: pointer;
  transition: background-color var(--dur-fast) ease, color var(--dur-fast) ease;
}
.seg button:hover {
  color: var(--text);
}
.seg button.on {
  background: var(--green-700);
  color: #fff;
}
.mode-hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-muted);
}

/* ---------------- 清单 ---------------- */
.list {
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-3) var(--sp-5);
  background: var(--paper-sunken);
  border-bottom: 1px solid var(--line);
}
.count {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.item {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-4) var(--sp-5);
  cursor: pointer;
  transition: background-color var(--dur-fast) ease;
}
.item + .item {
  border-top: 1px solid var(--line);
}
.item:hover {
  background: var(--green-50);
}
.item.picked {
  background: var(--paper-raised);
}
/* 选中行左侧压一道琥珀线 */
.item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--amber-600);
  transform: scaleY(0);
  transition: transform var(--dur-base) var(--ease-out);
}
.item.picked::before {
  transform: scaleY(1);
}

.cover {
  width: 46px;
  height: 58px;
  flex-shrink: 0;
  border-radius: var(--r-sm);
  overflow: hidden;
  background: var(--paper-sunken);
}
.cover-img {
  width: 100%;
  height: 100%;
}
.cover-blank {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--text-muted);
}

.meta {
  flex: 1;
  min-width: 0;
}
.meta h3 {
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta p {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.amount {
  text-align: right;
  flex-shrink: 0;
}
.price {
  display: block;
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--amber-700);
}
.amount em {
  font-size: 11px;
  font-style: normal;
  color: var(--text-muted);
}

.del {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border: none;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--dur-fast) ease, color var(--dur-fast) ease;
}
.del:hover {
  background: rgba(180, 69, 60, 0.1);
  color: var(--c-danger);
}

/* ---------------- 吸底结算 ---------------- */
.settle {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-top: var(--sp-4);
  padding: var(--sp-4) var(--sp-5);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: saturate(150%) blur(10px);
  -webkit-backdrop-filter: saturate(150%) blur(10px);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-2);
}
.picked-line {
  font-size: var(--fs-sm);
  color: var(--text-secondary);
}
.picked-line b {
  color: var(--text);
  font-weight: 500;
}
.rest {
  margin-left: 8px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.total-line {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  margin-top: 4px;
}
.total-line .k {
  font-size: var(--fs-xs);
  letter-spacing: 0.06em;
  color: var(--text-muted);
}
.total-line b {
  font-size: 24px;
  font-weight: 500;
  color: var(--amber-700);
  letter-spacing: -0.02em;
}

@media (max-width: 640px) {
  .mode-head {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-3);
  }
  .seg {
    width: 100%;
  }
  .seg button {
    flex: 1;
    justify-content: center;
  }
  .item {
    padding: var(--sp-3) var(--sp-4);
    gap: var(--sp-3);
  }
  .settle {
    flex-direction: column;
    align-items: stretch;
    text-align: center;
  }
  .settle-line,
  .picked-line,
  .total-line {
    justify-content: center;
  }
}
</style>
