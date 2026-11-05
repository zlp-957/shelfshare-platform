<template>
  <div class="checkout-page">
    <header class="page-title">
      <h2>取书核销台</h2>
      <span class="hint">到馆取书与归还都在这里办理，输入框支持扫码枪直接录入</span>
    </header>

    <el-tabs v-model="tab" class="checkout-tabs">
      <!-- 取书核销：借阅与购买统一在这里办 -->
      <el-tab-pane label="取书核销" name="pickup">
        <div class="panel">
          <p class="panel-tip">
            读者到馆取书时，输入他的<b>取书码</b>（6 位）或<b>读者账号</b>，
            勾选要交付的图书后一次核销。借阅与购买的取书都在这里办。
          </p>

          <div class="search-bar">
            <el-input
              ref="keywordInput"
              v-model="keyword"
              placeholder="取书码 6 位，或读者账号 R20260001"
              clearable
              style="max-width: 340px"
              @keyup.enter="doQuery"
            />
            <el-button type="primary" :loading="queryLoading" @click="doQuery">查询</el-button>
          </div>

          <template v-if="pending">
            <div class="user-bar">
              <span class="uname">{{ pending.userName }}</span>
              <span class="uacc">{{ pending.userAccount }}</span>
              <span v-if="pending.phone" class="uphone">手机 {{ pending.phone }}</span>
              <span class="ucount">待取 {{ pending.totalCount }} 件</span>
            </div>

            <el-empty
              v-if="rows.length === 0"
              description="这位读者没有待取的图书"
              :image-size="80"
            />

            <template v-else>
              <el-table
                ref="tableRef"
                :data="rows"
                row-key="key"
                stripe
                @selection-change="onSelectionChange"
              >
                <el-table-column type="selection" width="48" />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.kind === 'borrow' ? 'warning' : 'success'" size="small" effect="light">
                      {{ row.kind === 'borrow' ? '借阅' : '购买' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="bookName" label="书名" min-width="170" />
                <el-table-column label="副本条码" width="140">
                  <template #default="{ row }">
                    <span class="mono">{{ row.barcode || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="金额" width="120">
                  <template #default="{ row }">
                    <span class="num">￥{{ money(row.amount) }}</span>
                    <span class="amount-hint">{{ row.kind === 'borrow' ? '押金' : '书款' }}</span>
                  </template>
                </el-table-column>
              </el-table>

              <div class="settle-bar">
                <span class="picked">已勾选 <b>{{ selected.length }}</b> 件</span>
                <el-button
                  type="primary"
                  :loading="pickupLoading"
                  :disabled="selected.length === 0"
                  @click="doPickupAll"
                >
                  确认核销
                </el-button>
              </div>
            </template>
          </template>

          <!-- 待查询：给柜员一个明确的起点，而不是留一片空白 -->
          <div v-else class="standby">
            <div class="standby-mark">
              <el-icon :size="24"><Search /></el-icon>
            </div>
            <p class="standby-title">等待查询</p>
            <p class="standby-sub">输入取书码或读者账号后，这里会列出该读者名下所有待取图书</p>
            <ul class="standby-tips">
              <li>扫码枪可直接录入，回车即查询</li>
              <li>取书码 30 分钟内有效</li>
              <li>借阅与购买的取书一并核销</li>
            </ul>
          </div>
        </div>
      </el-tab-pane>

      <!-- 归还核销 -->
      <el-tab-pane label="归还核销" name="in">
        <div class="panel">
          <p class="panel-tip">
            还书只需扫描副本条码，系统会自动识别借阅人并计算是否逾期。如书有破损，填写赔偿金额后一并结算。
          </p>
          <el-form label-width="96px" class="checkout-form">
            <el-form-item label="副本条码">
              <el-input
                ref="inBarcodeInput"
                v-model="inForm.barcode"
                placeholder="扫码或手动输入，如 B001001"
                clearable
                @keyup.enter="doCheckIn"
              />
            </el-form-item>
            <el-form-item label="破损赔偿">
              <el-input-number v-model="inForm.damageFee" :min="0" :precision="2" :step="5" />
              <span class="form-hint">无破损留 0，从押金中扣除</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="inLoading" @click="doCheckIn">
                确认归还并结算
              </el-button>
              <el-button @click="resetIn">清空</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 归还结算结果 -->
    <el-card v-if="result" class="result-card" shadow="never">
      <template #header>
        <div class="result-header">
          <span>核销结果</span>
          <el-tag :type="result.status === 2 ? 'success' : 'primary'" effect="light">
            {{ result.statusText }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="图书">{{ result.bookName }}</el-descriptions-item>
        <el-descriptions-item label="副本条码">{{ result.barcode }}</el-descriptions-item>
        <el-descriptions-item label="读者">
          {{ result.userName }}（{{ result.userAccount }}）
        </el-descriptions-item>
        <el-descriptions-item label="押金">￥{{ money(result.depositAmount) }}</el-descriptions-item>
        <el-descriptions-item v-if="result.overdueDays > 0" label="逾期天数">
          <span class="warn">{{ result.overdueDays }} 天</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="result.overdueFee > 0" label="逾期费">
          ￥{{ money(result.overdueFee) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="result.damageFee > 0" label="破损赔偿">
          ￥{{ money(result.damageFee) }}
        </el-descriptions-item>
        <el-descriptions-item label="本次扣除">
          <span class="warn">￥{{ money(result.deductAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="退还押金">
          <span class="ok">￥{{ money(result.refundAmount) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <p v-if="result.refundAmount > 0" class="result-tip">
        退款将原路退回读者支付宝账户，通常 1～3 个工作日到账。
      </p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkIn, getPickupPending, doPickup } from '../../api'

const tab = ref('pickup')
const keywordInput = ref(null)
const inBarcodeInput = ref(null)
const tableRef = ref(null)

const keyword = ref('')
const queryLoading = ref(false)
const pending = ref(null)
const selected = ref([])
const pickupLoading = ref(false)

const inForm = reactive({ barcode: '', damageFee: 0 })
const inLoading = ref(false)
const result = ref(null)

function money(v) {
  const n = Number(v || 0)
  return n.toFixed(2)
}

// 把借阅待取与购买待取摊平成一张表，方便统一勾选
const rows = computed(() => {
  if (!pending.value) return []
  const b = (pending.value.borrows || []).map((x) => ({
    key: 'b-' + x.id,
    kind: 'borrow',
    id: x.id,
    bookName: x.bookName,
    barcode: x.barcode,
    amount: x.depositAmount
  }))
  const p = (pending.value.purchases || []).map((x) => ({
    key: 'p-' + x.id,
    kind: 'purchase',
    id: x.id,
    bookName: x.bookName,
    barcode: x.barcode,
    amount: x.price
  }))
  return [...b, ...p]
})

function onSelectionChange(list) {
  selected.value = list
}

async function doQuery() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入取书码或读者账号')
    return
  }
  queryLoading.value = true
  try {
    const res = await getPickupPending(kw)
    pending.value = res.data
    selected.value = []
    // 查出来就是要给他的书，默认全选，可手动取消
    await nextTick()
    tableRef.value?.toggleAllSelection()
  } catch (e) {
    pending.value = null
    ElMessage.error(e?.message || '查询失败')
  } finally {
    queryLoading.value = false
  }
}

async function doPickupAll() {
  if (selected.value.length === 0) {
    ElMessage.warning('请勾选要交付的图书')
    return
  }
  const borrowIds = selected.value.filter((r) => r.kind === 'borrow').map((r) => r.id)
  const orderIds = selected.value.filter((r) => r.kind === 'purchase').map((r) => r.id)
  pickupLoading.value = true
  try {
    const res = await doPickup({
      keyword: keyword.value.trim(),
      borrowIds,
      orderIds
    })
    const r = res.data || {}
    const okSummary = `借阅 ${r.borrowSuccess || 0} 本、购买 ${r.purchaseSuccess || 0} 本`
    if (r.failures && r.failures.length > 0) {
      await ElMessageBox.alert(
        `已完成：${okSummary}\n\n以下未成功：\n${r.failures.join('\n')}`,
        '部分未成功',
        { type: 'warning' }
      )
    } else {
      ElMessage.success(`核销完成：${okSummary}`)
    }
    // 核销后取书码已作废，清空现场让下一位读者重新报码
    keyword.value = ''
    pending.value = null
    selected.value = []
    nextTick(() => keywordInput.value?.focus())
  } catch (e) {
    ElMessage.error(e?.message || '核销失败')
  } finally {
    pickupLoading.value = false
  }
}

async function doCheckIn() {
  if (!inForm.barcode.trim()) {
    ElMessage.warning('请扫描或输入副本条码')
    return
  }
  inLoading.value = true
  try {
    const res = await checkIn({
      barcode: inForm.barcode.trim(),
      damageFee: inForm.damageFee || 0
    })
    result.value = res.data
    ElMessage.success('归还成功')
    resetIn()
  } catch (e) {
    ElMessage.error(e?.message || '核销失败')
  } finally {
    inLoading.value = false
  }
}

function resetIn() {
  inForm.barcode = ''
  inForm.damageFee = 0
  nextTick(() => inBarcodeInput.value?.focus())
}

onMounted(() => {
  nextTick(() => keywordInput.value?.focus())
})

// 切换 tab 时把焦点放到对应输入框，方便扫码枪直接工作
watch(tab, (v) => {
  nextTick(() => {
    if (v === 'pickup') keywordInput.value?.focus()
    else inBarcodeInput.value?.focus()
  })
})
</script>

<style scoped>
.checkout-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 标签页：指示条用琥珀色，与全站强调色一致 */
.checkout-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--sp-5);
}
.checkout-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: var(--line);
}
.checkout-tabs :deep(.el-tabs__item) {
  height: 44px;
  font-size: var(--fs-base);
  color: var(--text-secondary);
  letter-spacing: 0.03em;
}
.checkout-tabs :deep(.el-tabs__item.is-active) {
  color: var(--green-700);
  font-weight: 500;
}
.checkout-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--amber-600);
}

.panel {
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
}

.panel-tip {
  font-size: var(--fs-sm);
  color: var(--text-muted);
  margin: 0 0 var(--sp-5);
  line-height: 1.8;
}
.panel-tip b {
  color: var(--text-secondary);
  font-weight: 500;
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

/* 待查询态 */
.standby {
  padding: var(--sp-7) var(--sp-5) var(--sp-6);
  text-align: center;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  background: var(--paper);
}
.standby-mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin: 0 auto var(--sp-4);
  border-radius: 50%;
  background: var(--paper-sunken);
  color: var(--text-muted);
}
.standby-title {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}
.standby-sub {
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--text-muted);
}
.standby-tips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--sp-2) var(--sp-5);
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
  list-style: none;
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.standby-tips li {
  position: relative;
  padding-left: 12px;
}
.standby-tips li::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--amber-600);
  transform: translateY(-50%);
}

/* 读者信息条：左侧琥珀压线，一眼确认「现在服务的是谁」 */
.user-bar {
  position: relative;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-5);
  margin-bottom: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--paper-sunken);
  border: 1px solid var(--line);
}
.user-bar::before {
  content: "";
  position: absolute;
  left: -1px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 3px;
  background: var(--amber-600);
}
.uname {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--text);
}
.uacc {
  font-family: var(--font-mono);
  font-size: var(--fs-sm);
  color: var(--text-muted);
  letter-spacing: 0.02em;
}
.uphone {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}
.ucount {
  margin-left: auto;
  padding: 3px 12px;
  border-radius: var(--r-pill);
  background: var(--amber-100);
  border: 1px solid rgba(200, 134, 60, 0.28);
  font-size: var(--fs-xs);
  color: var(--amber-700);
  white-space: nowrap;
}

.mono {
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-secondary, #606266);
}
.num {
  font-family: var(--font-mono, monospace);
  font-variant-numeric: tabular-nums;
}
.amount-hint {
  margin-left: 6px;
  font-size: 11px;
  color: var(--text-muted);
}

.settle-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line, rgba(0, 0, 0, 0.06));
}
.picked {
  font-size: 13px;
  color: var(--text-secondary, #606266);
}
.picked b {
  color: var(--text);
}

.checkout-form {
  max-width: 560px;
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-tertiary, #909399);
}

/* 核销结果：按收据排版，金额行用等宽数字，方便柜面对账 */
.result-card {
  border-radius: var(--r-lg);
  border-color: var(--line-strong);
}
.result-header {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}
.result-header > span:first-child {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  font-weight: 600;
}
.result-header .el-tag {
  margin-left: auto;
}

.result-card :deep(.el-descriptions__body) {
  background: transparent;
}
.result-card :deep(.el-descriptions__cell) {
  border-color: var(--line) !important;
  padding: 10px 14px;
}
.result-card :deep(.el-descriptions__label) {
  background: var(--paper-sunken) !important;
  color: var(--text-muted) !important;
  font-size: var(--fs-xs) !important;
  font-weight: 400 !important;
  letter-spacing: 0.06em;
  width: 96px;
}
.result-card :deep(.el-descriptions__content) {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  color: var(--text) !important;
  font-size: var(--fs-sm) !important;
}

.result-tip {
  margin: var(--sp-4) 0 0;
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--line-strong);
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.warn {
  color: var(--c-danger);
  font-weight: 500;
}

.ok {
  color: var(--c-success);
  font-weight: 500;
}
</style>
