<template>
  <div class="ticket" :class="{ done: expired }">
    <!-- 左：码面 -->
    <div class="stub">
      <p class="label">
        <el-icon><Location /></el-icon>
        到馆取书码
      </p>

      <template v-if="hasPending">
        <div v-if="code" class="code-line">
          <span class="code">{{ codeText }}</span>
          <span class="countdown" :class="{ danger: expired }">{{ remainText }}</span>
        </div>
        <p v-else class="code-empty">尚未生成，点击右侧按钮获取</p>
      </template>
      <p v-else class="code-empty">
        暂无待取的图书 —— 借阅下单或购买支付后，这里才会出现取书码
      </p>
    </div>

    <!-- 撕线 -->
    <div class="perf"></div>

    <!-- 右：操作 -->
    <div class="act">
      <template v-if="hasPending">
        <el-button type="primary" :loading="loading" @click="handleGenerate">
          {{ code ? '刷新取书码' : '生成取书码' }}
        </el-button>
        <p class="hint">到馆报这 6 位码，工作人员可一次办理你的全部待取图书</p>
      </template>
      <p v-else class="hint">
        取书码只在实际有东西要取时才有意义：借阅已下单、或购买已支付待取，即可在这里生成
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyPickupCode, generatePickupCode } from '../api'

const code = ref('')
const expireAt = ref(null)
// 初始按"有"处理，等接口回来再定，避免已登录老用户看到闪动
const hasPending = ref(true)
const loading = ref(false)
const nowTs = ref(Date.now())
let timer = null

const expired = computed(() => {
  if (!expireAt.value) return false
  return new Date(expireAt.value).getTime() <= nowTs.value
})

// 6 位码分成 3+3，比一串连读更容易口头报出
const codeText = computed(() => {
  const c = String(code.value || '')
  return c.length === 6 ? `${c.slice(0, 3)} ${c.slice(3)}` : c
})

const remainText = computed(() => {
  if (!expireAt.value) return ''
  const left = new Date(expireAt.value).getTime() - nowTs.value
  if (left <= 0) return '已失效，请重新生成'
  const m = Math.floor(left / 60000)
  const s = Math.floor((left % 60000) / 1000)
  return `${m}:${String(s).padStart(2, '0')} 后失效`
})

async function load() {
  try {
    const res = await getMyPickupCode()
    code.value = res.data?.pickupCode || ''
    expireAt.value = res.data?.codeExpire || null
    hasPending.value = (res.data?.totalCount || 0) > 0
  } catch (e) {
    // 静默：只是辅助信息
  }
}

async function handleGenerate() {
  loading.value = true
  try {
    const res = await generatePickupCode()
    code.value = res.data.code
    // 后端只回码与有效分钟数，本地推算过期时刻
    expireAt.value = new Date(Date.now() + (res.data.expireMinutes || 30) * 60000).toISOString()
    hasPending.value = true
    ElMessage.success('取书码已生成，30 分钟内有效')
    nowTs.value = Date.now()
  } catch (e) {
    // 可能是待取需求刚消失（订单被取消等），拉一次最新状态把卡片切对
    load()
    ElMessage.error(e?.message || '生成失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    nowTs.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.ticket {
  display: flex;
  align-items: stretch;
  margin-bottom: var(--sp-5);
  background: var(--paper-raised);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-lg);
  overflow: hidden;
}

/* ---------------- 码面 ---------------- */
.stub {
  flex: 1;
  min-width: 0;
  padding: var(--sp-4) var(--sp-5);
  background:
    linear-gradient(180deg, var(--amber-100) 0%, var(--paper-raised) 78%);
}
.label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-xs);
  letter-spacing: 0.12em;
  color: var(--amber-700);
}
.code-line {
  display: flex;
  align-items: baseline;
  gap: var(--sp-4);
  margin-top: var(--sp-2);
  flex-wrap: wrap;
}
.code {
  font-family: var(--font-mono);
  font-size: 34px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: 0.1em;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.countdown {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.countdown.danger {
  color: var(--c-danger);
}
.code-empty {
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-muted);
}
/* 失效态：码变灰，避免误用 */
.ticket.done .code {
  color: var(--text-muted);
  text-decoration: line-through;
  text-decoration-thickness: 2px;
}

/* ---------------- 撕线 ---------------- */
.perf {
  position: relative;
  width: 0;
  border-left: 1px dashed var(--line-strong);
  flex-shrink: 0;
}
/* 上下两个缺口，做成票券撕口 */
.perf::before,
.perf::after {
  content: "";
  position: absolute;
  left: -7px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--paper);
  border: 1px solid var(--line-strong);
}
.perf::before {
  top: -7px;
  clip-path: inset(50% 0 0 0);
}
.perf::after {
  bottom: -7px;
  clip-path: inset(0 0 50% 0);
}

/* ---------------- 操作区 ---------------- */
.act {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: var(--sp-2);
  width: 290px;
  flex-shrink: 0;
  padding: var(--sp-4) var(--sp-5);
}
.hint {
  max-width: 250px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--text-muted);
  text-align: right;
}

@media (max-width: 820px) {
  .ticket {
    flex-direction: column;
  }
  .perf {
    width: auto;
    border-left: none;
    border-top: 1px dashed var(--line-strong);
  }
  .perf::before,
  .perf::after {
    top: -7px;
    left: auto;
  }
  .perf::before {
    left: -7px;
    clip-path: inset(0 50% 0 0);
  }
  .perf::after {
    right: -7px;
    left: auto;
    clip-path: inset(0 0 0 50%);
  }
  .act {
    width: auto;
    align-items: stretch;
    text-align: center;
  }
  .hint {
    max-width: none;
    text-align: center;
  }
}
</style>
