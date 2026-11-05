<template>
  <button
    class="u-blob-btn"
    :class="{ 'is-loading': loading, 'is-disabled': disabled }"
    :disabled="loading || disabled"
    type="button"
    @click="$emit('click', $event)"
  >
    <span class="text">
      <span v-if="loading" class="dot-spin"><i></i><i></i><i></i><i></i></span>
      <template v-else>{{ text }}</template>
    </span>
    <span class="blob"></span>
    <span class="blob"></span>
    <span class="blob"></span>
    <span class="blob"></span>
  </button>
</template>

<script setup>
/**
 * UBlobButton — 改造自 Uiverse.io 组件（MIT）
 * 来源: https://uiverse.io/adamgiebl/soft-gecko-85 (by adamgiebl)
 * 本地副本: C:/Users/zlp/.workbuddy/assets/uiverse-galaxy/Buttons/adamgiebl_soft-gecko-85.html
 * 改造: 玻璃质感 blob 悬停动效保留；配色由粉紫渐变换成项目令牌
 *       (墨绿底 + 琥珀/松绿 blob)；支持 loading / disabled；宽度撑满父容器。
 */
defineProps({
  text: { type: String, required: true },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})
defineEmits(['click'])
</script>

<style scoped>
.u-blob-btn {
  position: relative;
  width: 100%;
  height: 46px;
  font-family: inherit;
  font-size: 15px;
  letter-spacing: 0.3em;
  text-indent: 0.3em;
  border-radius: 40em;
  z-index: 1;
  color: #fff;
  overflow: hidden;
  border: none;
  cursor: pointer;
  transition: transform 0.2s var(--ease-out, ease);
}
.u-blob-btn:not(.is-disabled):not(.is-loading):hover {
  transform: translateY(-1px);
}
.u-blob-btn:not(.is-disabled):not(.is-loading):active {
  transform: translateY(0);
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.55);
}
.u-blob-btn.is-disabled {
  cursor: not-allowed;
  filter: saturate(0.35) brightness(0.92);
}

.u-blob-btn .text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 40em;
  background: linear-gradient(rgba(255, 255, 255, 0.34), rgba(27, 42, 36, 0.32));
  z-index: 1;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(27, 42, 36, 0.55);
}

.u-blob-btn .blob {
  position: absolute;
  z-index: 0;
  border-radius: 5em;
  /* 原组件按钮仅 8em 宽、blob 用 em 定位即可铺满；
     本组件宽度撑满表单（约 34em），必须改百分比定位才能覆盖全宽 */
  width: 42%;
  height: 150%;
  transition: transform 0.3s ease-in-out, background 0.3s ease-in-out;
}
/* 静态：松绿 → 琥珀 → 深金 → 墨绿（贴合图书馆配色），四块接力铺满 */
.u-blob-btn .blob:nth-of-type(1) { left: -6%;  top: -25%; background: #3e7a66; }
.u-blob-btn .blob:nth-of-type(2) { left: 20%;  top: -25%; background: #c8863c; }
.u-blob-btn .blob:nth-of-type(3) { left: 46%;  top: -25%; background: #e0a458; }
.u-blob-btn .blob:nth-of-type(4) { left: 70%;  top: -25%; background: #2f5d50; }

/* 悬停：色块交换 + 放大（uiverse 原始交互） */
.u-blob-btn:not(.is-disabled):not(.is-loading):hover .blob:nth-of-type(1) { background: #e0a458; }
.u-blob-btn:not(.is-disabled):not(.is-loading):hover .blob:nth-of-type(2) { background: #3e7a66; }
.u-blob-btn:not(.is-disabled):not(.is-loading):hover .blob:nth-of-type(3) { background: #2f5d50; }
.u-blob-btn:not(.is-disabled):not(.is-loading):hover .blob:nth-of-type(4) { background: #c8863c; }
.u-blob-btn:not(.is-disabled):not(.is-loading):hover .blob {
  transform: scale(1.18);
}

/* loading 时的四点旋转（与 blob 按钮气质一致的轻量指示器） */
.dot-spin {
  display: inline-flex;
  gap: 4px;
  position: relative;
  width: 26px;
  height: 16px;
  text-indent: 0;
  letter-spacing: 0;
}
.dot-spin i {
  position: absolute;
  top: 5px;
  left: 11px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: dot-orbit 1s linear infinite;
}
.dot-spin i:nth-child(2) { animation-delay: 0.12s; }
.dot-spin i:nth-child(3) { animation-delay: 0.24s; }
.dot-spin i:nth-child(4) { animation-delay: 0.36s; }
@keyframes dot-orbit {
  from { transform: rotate(0deg) translateX(9px) rotate(0deg); opacity: 1; }
  50%  { opacity: 0.35; }
  to   { transform: rotate(360deg) translateX(9px) rotate(-360deg); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .u-blob-btn .blob, .dot-spin i { animation: none !important; transition: none !important; }
}
</style>
