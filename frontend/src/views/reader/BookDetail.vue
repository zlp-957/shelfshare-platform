<template>
  <div class="detail-wrap">
    <!-- 加载态 -->
    <div v-if="!book" class="detail">
      <div class="cover cover--ghost"></div>
      <div class="info">
        <div class="ghost-line" style="width: 46%; height: 28px"></div>
        <div class="ghost-line" style="width: 70%"></div>
        <div class="ghost-line" style="width: 32%"></div>
      </div>
    </div>

    <article v-else class="detail">
      <!-- 返回：放左上角，独立于操作按钮 -->
      <button class="back-link" @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>返回列表
      </button>

      <div class="cover">
        <el-image v-if="book.cover" :src="book.cover" fit="cover" class="cover-img" />
        <div v-else class="cover-blank">
          <el-icon :size="48"><Notebook /></el-icon>
          <span>{{ book.name }}</span>
        </div>
      </div>

      <div class="info">
        <p class="catalog-no">馆藏编号 · #{{ String(book.id).padStart(4, '0') }}</p>
        <h1 class="title">{{ book.name }}</h1>

        <dl class="meta">
          <div class="meta-cell">
            <dt>分类</dt>
            <dd>{{ book.typeName || '未分类' }}</dd>
          </div>
          <div class="meta-cell">
            <dt>作者</dt>
            <dd>{{ book.author || '未知' }}</dd>
          </div>
          <div class="meta-cell">
            <dt>ISBN</dt>
            <dd class="num">{{ book.isbn || '暂无' }}</dd>
          </div>
          <div class="meta-cell">
            <dt>库存</dt>
            <dd :class="book.stock > 0 ? 'ok' : 'out'">
              {{ book.stock > 0 ? book.stock + ' 本在库' : '已售罄' }}
            </dd>
          </div>
        </dl>

        <div class="price-block">
          <span class="price-label">售价</span>
          <span class="price-value num">¥{{ book.price }}</span>
        </div>

        <div class="actions">
          <!-- 借阅 / 购买 / 预约，按当前用户状态切换 -->
          <template v-if="userStatus === 0">
            <el-button v-if="book.stock > 0" type="primary" size="large" :loading="loading" @click="handleBorrow">
              <el-icon style="margin-right: 6px"><Tickets /></el-icon>借阅（押金 ¥{{ book.price }}）
            </el-button>
            <el-button v-if="book.stock > 0" size="large" class="buy-btn" :loading="loading" @click="handleBuy">
              <el-icon style="margin-right: 6px"><ShoppingCart /></el-icon>购买 ¥{{ book.price }}
            </el-button>
            <el-button v-if="book.stock <= 0" type="warning" size="large" :loading="loading" @click="handleReserve">
              <el-icon style="margin-right: 6px"><Timer /></el-icon>预约排队
            </el-button>
          </template>

          <template v-else-if="userStatus === 1">
            <el-button type="info" size="large" disabled>借阅中</el-button>
          </template>
          <template v-else-if="userStatus === 2">
            <el-button type="info" size="large" disabled>待取书</el-button>
          </template>
          <template v-else-if="userStatus === 3">
            <el-button type="warning" size="large" @click="gotoBorrows">待支付押金 · 去支付</el-button>
          </template>
          <template v-else-if="userStatus === 4">
            <el-button type="info" size="large" disabled>已预约 · 第 {{ queueNo }} 位</el-button>
          </template>
          <template v-else-if="userStatus === 5">
            <el-button type="info" size="large" disabled>已预约 · 待取书</el-button>
          </template>

          <!-- 加购只是一个"先存着"的动作，借阅还是购买留到购物车里再定 -->
          <el-button
            v-if="userStatus === 0 && book.stock > 0"
            size="large"
            class="cart-btn"
            :loading="loading"
            @click="addToCart"
          >
            <el-icon style="margin-right: 6px"><ShoppingCart /></el-icon>
            {{ inCart ? '已在购物车' : '加入购物车' }}
          </el-button>
        </div>

        <!-- 购买过只是既成事实，不阻塞借阅与再次购买 -->
        <div v-if="book.purchased" class="purchased-note">
          <el-icon><CircleCheck /></el-icon>
          <span>你已购买过这本书 —— 仍可从馆藏继续借阅，也可以再买一本</span>
        </div>

        <el-alert
          v-if="book.stock <= 0"
          type="warning"
          :closable="false"
          show-icon
          class="tip"
          title="该书已全部借出，可提交预约排队，有书归还后按顺序通知您"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          class="tip"
          title="借阅免费，仅需缴纳与书价等额的押金，按期归还后全额退还；购买则书归您所有，不退不换"
        />

        <section class="intro">
          <h2>图书简介</h2>
          <p>{{ book.intro || '暂无简介' }}</p>
        </section>
      </div>
    </article>

    <AgreementDialog
      v-model="agreementVisible"
      :type="agreementType"
      @agreed="onAgreed"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBookDetail, applyBorrow, buyBook, reserveBook } from '../../api'
import { submitPayForm } from '../../utils/pay'
import AgreementDialog from '../../components/AgreementDialog.vue'
import { useCartStore } from '../../stores/cart'

const route = useRoute()
const router = useRouter()
const book = ref(null)
const loading = ref(false)
const cartStore = useCartStore()

// 规则确认：掏钱之前必须先知晓
const agreementVisible = ref(false)
const agreementType = ref(1)
const pendingAction = ref(null)

const userStatus = computed(() => book.value?.userStatus ?? 0)
const queueNo = computed(() => book.value?.queueNo ?? null)
const inCart = computed(() => book.value ? cartStore.has(book.value.id) : false)

const loadDetail = async () => {
  const res = await getBookDetail(route.params.id)
  book.value = res.data
}

const handleBorrow = () => {
  agreementType.value = 1
  pendingAction.value = 'borrow'
  agreementVisible.value = true
}

const handleBuy = () => {
  agreementType.value = 2
  pendingAction.value = 'buy'
  agreementVisible.value = true
}

const gotoBorrows = () => {
  router.push('/reader/borrows')
}

async function onAgreed() {
  const action = pendingAction.value
  pendingAction.value = null
  if (action === 'borrow') await doBorrow()
  if (action === 'buy') await doBuy()
}

async function doBorrow() {
  loading.value = true
  try {
    const res = await applyBorrow(book.value.id)
    ElMessage.success('押金支付单已生成，正在跳转支付')
    await submitPayForm(res.data.payForm, res.data.orderNo)
  } catch (e) {
    ElMessage.error(e?.message || '发起支付失败')
  } finally {
    loading.value = false
  }
}

async function doBuy() {
  loading.value = true
  try {
    const res = await buyBook(book.value.id)
    ElMessage.success('支付单已生成，正在跳转支付')
    await submitPayForm(res.data.payForm, res.data.orderNo)
  } catch (e) {
    ElMessage.error(e?.message || '发起支付失败')
  } finally {
    loading.value = false
  }
}

const handleReserve = async () => {
  try {
    await ElMessageBox.confirm(`确定要预约《${book.value.name}》吗？有书归还后将按排队顺序通知您。`, '预约确认', { type: 'info' })
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await reserveBook(book.value.id)
    ElMessage.success('预约成功，已进入排队队列')
    await loadDetail()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function addToCart() {
  if (cartStore.has(book.value.id)) {
    ElMessage.info('这本书已在购物车，可前往购物车选择借阅或购买')
    return
  }
  cartStore.add({
    bookId: book.value.id,
    name: book.value.name,
    author: book.value.author,
    cover: book.value.cover,
    price: book.value.price
  })
  ElMessage.success('已加入购物车，结算时可选择借阅或购买')
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-wrap {
  max-width: 1040px;
}

/* 返回链接：占满一行，在卡片内容之上 */
.back-link {
  grid-column: 1 / -1;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  width: fit-content;
  border: none;
  background: none;
  padding: 0;
  margin-bottom: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: color var(--dur-fast) ease;
}
.back-link:hover {
  color: var(--text);
}

.detail {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--sp-7);
  padding: var(--sp-6);
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
}

/* ---------------- 封面 ---------------- */
.cover {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--paper-sunken);
  box-shadow: var(--shadow-2);
  align-self: start;
}
.cover::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 10px;
  background: linear-gradient(
    90deg,
    rgba(27, 42, 36, 0.26),
    rgba(27, 42, 36, 0.05) 60%,
    transparent
  );
  pointer-events: none;
}
.cover-img {
  width: 100%;
  height: 100%;
}
.cover-blank {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  height: 100%;
  padding: var(--sp-5);
  color: var(--text-muted);
  background-image: repeating-linear-gradient(
    135deg,
    rgba(27, 42, 36, 0.025) 0 1px,
    transparent 1px 10px
  );
}
.cover-blank span {
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  text-align: center;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* ---------------- 信息区 ---------------- */
.catalog-no {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  letter-spacing: 0.12em;
  color: var(--amber-700);
  margin-bottom: var(--sp-2);
}
.title {
  font-family: var(--font-serif);
  font-size: var(--fs-xl);
  font-weight: 600;
  line-height: 1.3;
  color: var(--text);
  margin-bottom: var(--sp-5);
}

/* 元信息：定义列表 + 发丝分隔，比一排标签更稳 */
.meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--sp-4);
  padding: var(--sp-4) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.meta-cell dt {
  font-size: var(--fs-xs);
  letter-spacing: 0.1em;
  color: var(--text-muted);
  margin-bottom: 4px;
}
.meta-cell dd {
  font-size: var(--fs-base);
  color: var(--text);
  margin: 0;
}
.meta-cell dd.ok {
  color: var(--c-success);
  font-weight: 500;
}
.meta-cell dd.out {
  color: var(--c-danger);
  font-weight: 500;
}

.price-block {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  margin: var(--sp-5) 0;
}
.price-label {
  font-size: var(--fs-sm);
  letter-spacing: 0.1em;
  color: var(--text-muted);
}
.price-value {
  font-size: var(--fs-2xl);
  font-weight: 500;
  color: var(--amber-700);
  letter-spacing: -0.02em;
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
  margin-bottom: var(--sp-4);
}
/* 购买按钮：次级动作，描边不抢主色 */
.buy-btn {
  background: transparent;
  border-color: var(--amber-600);
  color: var(--amber-700);
}
.buy-btn:hover {
  background: var(--amber-100);
  border-color: var(--amber-600);
  color: var(--amber-700);
}
/* 购物车按钮：更淡的次级动作 */
.cart-btn {
  background: transparent;
  border-color: var(--line-strong);
  color: var(--text-secondary);
}
.cart-btn:hover {
  background: var(--paper-sunken);
  border-color: var(--line-strong);
  color: var(--text);
}

/* 已购买提示：信息性，不使用禁用态样式，避免看起来"这条路走不通" */
.purchased-note {
  display: flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  margin: 0 0 var(--sp-4);
  padding: 7px 12px;
  border-radius: var(--r-sm);
  background: rgba(29, 158, 117, 0.1);
  color: var(--c-success);
  font-size: var(--fs-xs);
  line-height: 1.5;
}

.tip {
  max-width: 560px;
  margin-bottom: var(--sp-6);
}

.intro h2 {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  margin-bottom: var(--sp-3);
  padding-left: var(--sp-3);
  border-left: 3px solid var(--amber-600);
}
.intro p {
  font-size: var(--fs-base);
  line-height: 1.9;
  color: var(--text-secondary);
  max-width: 62ch;
}

/* ---------------- 加载骨架 ---------------- */
.cover--ghost {
  background: var(--paper-sunken);
  border: 1px dashed var(--line-strong);
  box-shadow: none;
  animation: pulse 1.4s ease-in-out infinite;
}
.ghost-line {
  height: 16px;
  margin-bottom: var(--sp-4);
  border-radius: var(--r-sm);
  background: var(--paper-sunken);
  animation: pulse 1.4s ease-in-out infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

@media (max-width: 820px) {
  .detail {
    grid-template-columns: 1fr;
    gap: var(--sp-5);
    padding: var(--sp-4);
  }
  .cover {
    max-width: 220px;
  }
  .title {
    font-size: var(--fs-lg);
  }
}
</style>
