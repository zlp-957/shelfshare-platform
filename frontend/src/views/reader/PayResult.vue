<template>
  <div class="pay-page">
    <div class="pay-card" :class="`is-${tone}`">
      <!-- 状态区 -->
      <header class="head">
        <div class="mark">
          <el-icon :size="30">
            <Loading v-if="checking" class="spin" />
            <CircleCheckFilled v-else-if="paid" />
            <WarningFilled v-else />
          </el-icon>
        </div>
        <h1>{{ checking ? '正在确认支付结果' : title }}</h1>
        <p class="sub">{{ checking ? '正在与支付宝核对这笔订单，请稍候…' : subTitle }}</p>
      </header>

      <!-- 凭据条 -->
      <div class="receipt">
        <div class="receipt-row">
          <span class="k">订单编号</span>
          <span class="v num">{{ orderNo || '—' }}</span>
        </div>
        <div class="receipt-row">
          <span class="k">支付状态</span>
          <span class="v">
            <el-tag :type="paid ? 'success' : 'warning'" effect="light" size="small">
              {{ checking ? '核对中' : paid ? '已到账' : '待确认' }}
            </el-tag>
          </span>
        </div>
      </div>

      <!-- 后续指引 -->
      <section class="steps">
        <h2>接下来</h2>
        <ol>
          <li>
            <span class="idx">01</span>
            <div>
              <strong>到馆出示借阅单</strong>
              <p>向工作人员报出订单编号，或直接打开「我的借阅」出示待取列表</p>
            </div>
          </li>
          <li>
            <span class="idx">02</span>
            <div>
              <strong>工作人员核销</strong>
              <p>扫描图书副本条码完成交付，<b>借期从核销那一刻开始计算</b></p>
            </div>
          </li>
          <li>
            <span class="idx">03</span>
            <div>
              <strong>归还后退押金</strong>
              <p>押金原路退回支付宝，通常 1～3 个工作日到账</p>
            </div>
          </li>
        </ol>
      </section>

      <div class="actions">
        <el-button type="primary" size="large" @click="$router.push('/reader/borrows')">
          查看我的借阅
        </el-button>
        <el-button size="large" text @click="$router.push('/reader')">返回书架</el-button>
      </div>

      <p v-if="!paid && !checking" class="footnote">
        若已扣款但状态迟迟不更新，请保留订单编号联系管理员核对，不要重复下单。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPayStatus } from '../../api'

const route = useRoute()
const paid = ref(false)
const checking = ref(false)
const status = ref(0)

const orderNo = computed(() => route.query.out_trade_no || '')

const tone = computed(() => (paid.value ? 'success' : 'warning'))

const title = computed(() => (paid.value ? '支付成功' : '支付结果待确认'))
const subTitle = computed(() =>
  paid.value
    ? '押金已收到，请到馆取书。归还后押金将原路退回。'
    : '如果您已完成付款，请稍后到「我的借阅」查看状态；系统会自动与支付宝核对。'
)

onMounted(async () => {
  // return_url 跳回来只代表用户走完了收银台，不代表钱到账。
  // 这里主动查一次，后端会向支付宝查单并补入账。
  if (!orderNo.value) return
  checking.value = true
  try {
    const res = await getPayStatus(orderNo.value)
    status.value = res.data
    paid.value = res.data === 1
  } catch (e) {
    // 忽略，用户可到我的借阅查看
  } finally {
    checking.value = false
  }
})
</script>

<style scoped>
.pay-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--sp-6) var(--sp-4);
  background: var(--paper);
  /* 顶部一层极淡的琥珀氛围光，不铺满，避免抢主体 */
  background-image: radial-gradient(
    48rem 26rem at 50% -12%,
    rgba(200, 134, 60, 0.12),
    transparent 68%
  );
}

.pay-card {
  width: 100%;
  max-width: 620px;
  padding: var(--sp-7) var(--sp-6) var(--sp-6);
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-3);
}

/* ---------------- 状态区 ---------------- */
.head {
  text-align: center;
  padding-bottom: var(--sp-5);
}
.mark {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin: 0 auto var(--sp-4);
  border-radius: 50%;
  background: var(--amber-100);
  color: var(--amber-700);
  border: 1px solid rgba(200, 134, 60, 0.3);
}
.is-success .mark {
  background: var(--green-100);
  color: var(--green-700);
  border-color: rgba(47, 93, 80, 0.28);
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.head h1 {
  font-family: var(--font-serif);
  font-size: var(--fs-xl);
  font-weight: 600;
  margin-bottom: var(--sp-2);
}
.sub {
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--text-secondary);
  max-width: 42ch;
  margin: 0 auto;
}

/* ---------------- 凭据条 ---------------- */
.receipt {
  padding: var(--sp-4) var(--sp-5);
  background: var(--paper-sunken);
  border-radius: var(--r-md);
  border: 1px dashed var(--line-strong);
}
.receipt-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: 6px 0;
}
.receipt-row + .receipt-row {
  border-top: 1px solid var(--line);
}
.k {
  font-size: var(--fs-sm);
  color: var(--text-muted);
  letter-spacing: 0.06em;
}
.v {
  font-size: var(--fs-sm);
  color: var(--text);
  text-align: right;
  word-break: break-all;
}

/* ---------------- 后续步骤 ---------------- */
.steps {
  margin-top: var(--sp-6);
}
.steps h2 {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  margin-bottom: var(--sp-4);
  padding-left: var(--sp-3);
  border-left: 3px solid var(--amber-600);
}
.steps ol {
  list-style: none;
  display: grid;
  gap: var(--sp-4);
}
.steps li {
  display: flex;
  gap: var(--sp-4);
}
.idx {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--amber-700);
  letter-spacing: 0.06em;
  padding-top: 3px;
  flex-shrink: 0;
}
.steps strong {
  display: block;
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--text);
  margin-bottom: 2px;
}
.steps p {
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--text-secondary);
}
.steps b {
  color: var(--green-700);
  font-weight: 500;
}

/* ---------------- 动作区 ---------------- */
.actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  margin-top: var(--sp-6);
  padding-top: var(--sp-5);
  border-top: 1px solid var(--line);
}
.footnote {
  margin-top: var(--sp-4);
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--text-muted);
  text-align: center;
}

/* ---------------- 入场 ---------------- */
@media (prefers-reduced-motion: no-preference) {
  .pay-card {
    animation: rise 520ms var(--ease-out) both;
  }
  .receipt,
  .steps,
  .actions {
    animation: rise 520ms var(--ease-out) both;
  }
  .receipt {
    animation-delay: 80ms;
  }
  .steps {
    animation-delay: 150ms;
  }
  .actions {
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

@media (max-width: 560px) {
  .pay-card {
    padding: var(--sp-5) var(--sp-4);
  }
  .actions {
    flex-direction: column-reverse;
  }
  .actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
