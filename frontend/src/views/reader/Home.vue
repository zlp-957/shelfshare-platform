<template>
  <div ref="pageRef">
    <!-- 检索条：不再用卡片包裹，改为一条横向工具带 -->
    <div class="search-strip">
      <div class="search-fields">
        <el-input
          v-model="keyword"
          placeholder="搜索书名 / 作者 / ISBN"
          clearable
          size="large"
          class="kw"
          @keyup.enter="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="typeId"
          placeholder="全部分类"
          clearable
          size="large"
          class="type"
          @change="handleSearch"
        >
          <el-option v-for="t in types" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-button type="primary" size="large" @click="handleSearch">搜索</el-button>
      </div>
      <div class="search-meta">
        <span class="meta-label">馆藏</span>
        <span class="meta-value num">{{ total }}</span>
        <span class="meta-unit">本</span>
      </div>
    </div>

    <!-- 预约到书提醒 -->
    <el-alert v-if="notifyReserves.length > 0" type="success" :closable="false" class="notify" show-icon>
      <template #title>
        <span class="notify-title">您有 {{ notifyReserves.length }} 本预约图书可以借阅了</span>
        <span v-for="r in notifyReserves" :key="r.id" class="notify-item">
          《{{ r.bookName }}》有效期至 {{ formatTime(r.validDeadline) }}
        </span>
      </template>
    </el-alert>

    <!-- 书架 -->
    <div v-if="books.length > 0" class="shelf">
      <article
        v-for="(book, i) in books"
        :key="book.id"
        class="book"
        :style="{ animationDelay: (i % 6) * 45 + 'ms' }"
        @click="goDetail(book.id)"
      >
        <div class="cover">
          <el-image v-if="book.cover" :src="book.cover" fit="cover" class="cover-img" />
          <div v-else class="cover-blank">
            <el-icon :size="34"><Notebook /></el-icon>
            <span>{{ book.name }}</span>
          </div>
          <span v-if="book.stock <= 0" class="sold-out">缺货</span>
        </div>
        <div class="book-body">
          <h3 class="book-name" :title="book.name">{{ book.name }}</h3>
          <p class="book-author">{{ book.author || '佚名' }}</p>
          <div class="book-foot">
            <span class="book-price num">¥{{ book.price }}</span>
            <span class="book-stock num">
              {{ book.stock > 0 ? '在库 ' + book.stock : '可预约' }}
            </span>
          </div>
        </div>
      </article>
    </div>

    <USpinner v-if="loading && books.length === 0" :size="44" label="正在取书…" />

    <el-empty v-if="!loading && books.length === 0" description="没有找到匹配的图书" />

    <div class="pagination" v-if="total > 0">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { pageBooks, listBookTypes, myReserves } from '../../api'
import USpinner from '../../components/ui/USpinner.vue'

const router = useRouter()
const keyword = ref('')
const typeId = ref(null)
const books = ref([])
const types = ref([])
const notifyReserves = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)

// ---------------- 自适应分页 ----------------
// 书架是 auto-fill 网格，一行放几张随窗口宽度变。
// 每页固定 12 本的话，宽屏一行 7 张会排成 7+5，第二行缺角还错位翻页。
// 所以按「满两行再翻页」：每页容量 = 当前列数 × 2，窗口变化时重算并回到第一页。
const pageRef = ref(null)
let resizeTimer = null
let resizeObserver = null
let loadSeq = 0

function calcCols() {
  const el = pageRef.value
  if (!el) return false
  const w = el.clientWidth
  if (w <= 0) return false
  // 与 .shelf 的 CSS 参数保持一致：小屏 minmax(150px,1fr)，其余 180px，列间距 16px
  const min = w < 820 ? 150 : 180
  const gap = 16
  const c = Math.max(1, Math.floor((w + gap) / (min + gap)))
  if (c === Math.ceil(pageSize.value / 2)) return false
  pageSize.value = c * 2
  return true
}

const loadTypes = async () => {
  const res = await listBookTypes()
  types.value = res.data
}

const loadBooks = async () => {
  const seq = ++loadSeq
  loading.value = true
  try {
    const res = await pageBooks({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
      typeId: typeId.value || undefined
    })
    // 窗口缩放会连续触发重载，只让最后一次的结果生效，防止旧响应覆盖新数据
    if (seq !== loadSeq) return
    books.value = res.data.records
    total.value = res.data.total
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

const loadNotifyReserves = async () => {
  try {
    const res = await myReserves()
    notifyReserves.value = res.data.filter(r => r.status === 1)
  } catch (e) {
    // 忽略
  }
}

const handleSearch = () => {
  page.value = 1
  loadBooks()
}

const handlePageChange = (p) => {
  page.value = p
  loadBooks()
}

const goDetail = (id) => {
  router.push(`/reader/book/${id}`)
}

const formatTime = (time) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  calcCols()
  loadTypes()
  loadBooks()
  loadNotifyReserves()
  // 容器宽度变化 → 列数变化 → 每页容量变化，回到第一页重新铺满两行
  resizeObserver = new ResizeObserver(() => {
    clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      if (calcCols()) {
        page.value = 1
        loadBooks()
      }
    }, 200)
  })
  if (pageRef.value) resizeObserver.observe(pageRef.value)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  clearTimeout(resizeTimer)
})
</script>

<style scoped>
/* ---------------- 检索条 ---------------- */
.search-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-5);
  padding: var(--sp-4) 0;
  margin-bottom: var(--sp-5);
  border-bottom: 1px solid var(--line);
}
.search-fields {
  display: flex;
  gap: var(--sp-3);
  flex-wrap: wrap;
}
.kw {
  width: 320px;
}
.type {
  width: 170px;
}
.search-meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
  white-space: nowrap;
}
.meta-label {
  font-size: var(--fs-xs);
  letter-spacing: 0.12em;
  color: var(--text-muted);
}
.meta-value {
  font-size: var(--fs-lg);
  font-weight: 500;
  color: var(--text);
}
.meta-unit {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

/* ---------------- 预约提醒 ---------------- */
.notify {
  margin-bottom: var(--sp-5);
}
.notify-title {
  font-weight: 500;
  margin-right: var(--sp-3);
}
.notify-item {
  display: inline-block;
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  margin-right: var(--sp-4);
  padding: 2px 0;
}

/* ---------------- 书架 ---------------- */
.shelf {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--sp-4) var(--sp-4);
}

.book {
  cursor: pointer;
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
  transition: transform var(--dur-base) var(--ease-out),
    box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base) ease;
}
.book:hover {
  transform: translateY(-4px);
  border-color: var(--line-strong);
  box-shadow: var(--shadow-3);
}

/* 封面：左缘一道暗线模拟书脊 */
.cover {
  position: relative;
  aspect-ratio: 3 / 4;
  background: var(--paper-sunken);
  overflow: hidden;
}
.cover::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 7px;
  background: linear-gradient(
    90deg,
    rgba(27, 42, 36, 0.22),
    rgba(27, 42, 36, 0.04) 62%,
    transparent
  );
  pointer-events: none;
}
.cover-img {
  width: 100%;
  height: 100%;
  transition: transform var(--dur-slow) var(--ease-out);
}
.book:hover .cover-img {
  transform: scale(1.05);
}
.cover-blank {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  height: 100%;
  padding: var(--sp-4);
  color: var(--text-muted);
  background-image: repeating-linear-gradient(
    135deg,
    rgba(27, 42, 36, 0.025) 0 1px,
    transparent 1px 10px
  );
}
.cover-blank span {
  font-family: var(--font-serif);
  font-size: var(--fs-sm);
  line-height: 1.5;
  text-align: center;
  color: var(--text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.sold-out {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 2px 8px;
  border-radius: var(--r-sm);
  background: rgba(27, 42, 36, 0.82);
  color: var(--text-inverse);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.book-body {
  padding: var(--sp-3) var(--sp-4) var(--sp-4);
}
.book-name {
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  font-weight: 600;
  line-height: 1.45;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.book-author {
  margin-top: 4px;
  font-size: var(--fs-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.book-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line);
}
.book-price {
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--amber-700);
}
.book-stock {
  font-size: var(--fs-xs);
  color: var(--text-muted);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-6);
}

@media (prefers-reduced-motion: no-preference) {
  .book {
    animation: fadeUp 440ms var(--ease-out) both;
  }
}
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 720px) {
  .search-strip {
    flex-direction: column;
    align-items: flex-start;
  }
  .kw,
  .type {
    width: 100%;
  }
  .shelf {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
</style>
