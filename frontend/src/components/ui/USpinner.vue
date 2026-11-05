<template>
  <div class="u-spinner-wrap" :style="{ minHeight: size + 24 + 'px' }">
    <span
      class="u-spinner"
      :style="{ width: size + 'px', height: size + 'px', borderWidth: Math.max(4, Math.round(size / 8)) + 'px' }"
      role="status"
      aria-label="加载中"
    ></span>
    <span v-if="label" class="u-spinner-label">{{ label }}</span>
  </div>
</template>

<script setup>
/**
 * USpinner — 改造自 Uiverse.io 组件（MIT）
 * 来源: https://uiverse.io/aqfox/tiny-jellyfish-70 (by AqFox)
 * 本地副本: C:/Users/zlp/.workbuddy/assets/uiverse-galaxy/loaders/AqFox_tiny-jellyfish-70.html
 * 改造: double 边框旋转环保留；蓝色换成墨绿主体 + 琥珀左边框；
 *       补充居中容器与可选文字，供页面级加载态使用。
 */
defineProps({
  size: { type: Number, default: 40 },
  label: { type: String, default: '' }
})
</script>

<style scoped>
.u-spinner-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px 0;
}

.u-spinner {
  display: inline-block;
  box-sizing: border-box;
  border-style: double;
  border-color: var(--brand-600, #2f5d50);
  border-left-style: solid;
  border-left-color: var(--amber-500, #c8863c);
  border-radius: 50%;
  animation: u-spin 0.75s infinite linear;
}

.u-spinner-label {
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--text-muted, #8a9189);
}

@keyframes u-spin {
  to { transform: rotate(360deg); }
}
@media (prefers-reduced-motion: reduce) {
  .u-spinner { animation-duration: 2.4s; }
}
</style>
