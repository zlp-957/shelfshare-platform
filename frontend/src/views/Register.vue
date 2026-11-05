<template>
  <div class="auth">
    <!-- 左侧品牌区 -->
    <section class="auth-brand">
      <div class="brand-top">
        <div class="mark">
          <el-icon :size="20"><Reading /></el-icon>
        </div>
        <span class="brand-name">书屿</span>
      </div>

      <div class="brand-statement">
        <div class="rule"></div>
        <h1>注册一个读者账号<br />开始你的借阅</h1>
        <p class="brand-sub">读者账号由系统统一编码，无需邮箱验证</p>
      </div>

      <ol class="brand-steps">
        <li><span class="idx">01</span><span>账号由系统自动分配，格式为 <b>R2026 + 4 位编号</b>，如 R20260005</span></li>
        <li><span class="idx">02</span><span>密码至少 6 位，注册后可用于借阅与购买</span></li>
        <li><span class="idx">03</span><span>手机号请如实填写，图书馆需要时靠它联系你</span></li>
      </ol>
    </section>

    <!-- 右侧表单区 -->
    <section class="auth-panel">
      <div class="auth-card">
        <header class="auth-head">
          <p class="eyebrow">REGISTER · 读者注册</p>
          <h2>创建账号</h2>
        </header>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" class="auth-form">
          <el-form-item prop="name">
            <el-input v-model="form.name" placeholder="真实姓名（到馆取书时核对）" clearable>
              <template #prefix><el-icon><Postcard /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="form.phone" placeholder="手机号（到书通知与密码找回）" maxlength="11" clearable>
              <template #prefix><el-icon><Iphone /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码（至少6位）" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <UBlobButton
              text="注 册"
              class="submit"
              :loading="loading"
              @click="handleRegister"
            />
          </el-form-item>
        </el-form>

        <div class="account-notes">
          <p class="note-line">
            <el-icon><InfoFilled /></el-icon>
            <span>姓名请填写真实姓名，到馆取书时工作人员会核对身份</span>
          </p>
          <p class="note-line">
            <el-icon><InfoFilled /></el-icon>
            <span>手机号用于到书通知，以及忘记密码时核对身份找回账号</span>
          </p>
          <p class="note-line">
            <el-icon><InfoFilled /></el-icon>
            <span>账号由系统自动分配，注册成功后会在弹窗中告诉你</span>
          </p>
        </div>

        <div class="auth-foot">
          <span>已有账号？</span>
          <el-link type="primary" :underline="false" @click="$router.push('/login')">去登录</el-link>
        </div>
      </div>

      <p class="auth-meta">广东财经大学 · 图书借阅管理</p>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { register } from '../api'
import UBlobButton from '../components/ui/UBlobButton.vue'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  name: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const rules = {
  name: [{ required: true, message: '请填写真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号，方便图书馆在需要时联系你', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号应为 11 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleRegister = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await register({
        name: form.name,
        phone: form.phone,
        password: form.password
      })
      // 账号是系统分配的，用户本来不知道，必须明确告诉他，
      // 否则注册完就没法登录了。顺便带进登录页自动填上。
      const account = res.data?.account
      await ElMessageBox.alert(
        `你的读者账号是 ${account}

请记下这个账号，登录时要用到它。也可以在「个人中心」里随时查看。`,
        '注册成功',
        { confirmButtonText: '去登录', type: 'success' }
      )
      router.push({ path: '/login', query: { account } })
    } catch (e) {
      // 错误已在拦截器提示；用户取消弹窗也会走到这里，忽略即可
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  height: 100%;
  background: var(--paper);
}

/* ---------------- 左侧品牌区 ---------------- */
.auth-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: var(--sp-7) var(--sp-8);
  overflow: hidden;
  background: linear-gradient(165deg, #1b2a24 0%, #234238 62%, #1d3229 100%);
  color: var(--text-inverse);
}
.auth-brand::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    90deg,
    rgba(244, 242, 237, 0.05) 0 1px,
    transparent 1px 44px
  );
  pointer-events: none;
}
.auth-brand::after {
  content: "";
  position: absolute;
  right: -14%;
  top: -18%;
  width: 60%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(200, 134, 60, 0.28), transparent 65%);
  pointer-events: none;
}

.brand-top,
.brand-statement,
.brand-steps {
  position: relative;
  z-index: 1;
}
.brand-top {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}
.mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--r-md);
  background: rgba(244, 242, 237, 0.1);
  border: 1px solid rgba(244, 242, 237, 0.18);
}
.brand-name {
  font-size: var(--fs-md);
  font-weight: 500;
  letter-spacing: 0.08em;
}

.brand-statement .rule {
  width: 48px;
  height: 2px;
  background: var(--amber-500);
  margin-bottom: var(--sp-5);
}
.brand-statement h1 {
  font-family: var(--font-serif);
  font-size: clamp(28px, 3.1vw, 42px);
  font-weight: 600;
  line-height: 1.26;
  color: var(--text-inverse);
}
.brand-sub {
  margin-top: var(--sp-4);
  font-size: var(--fs-sm);
  letter-spacing: 0.1em;
  color: rgba(244, 242, 237, 0.62);
}

.brand-steps {
  list-style: none;
  counter-reset: step;
  display: grid;
  gap: var(--sp-3);
  border-top: 1px solid rgba(244, 242, 237, 0.14);
  padding-top: var(--sp-5);
  max-width: 440px;
}
.brand-steps li {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
  color: rgba(244, 242, 237, 0.78);
}
.brand-steps b {
  color: var(--amber-500);
  font-weight: 500;
}
.brand-steps .idx {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--amber-500);
  letter-spacing: 0.06em;
}

/* ---------------- 右侧表单区 ---------------- */
.auth-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--sp-6) var(--sp-5);
  background: var(--paper);
}
.auth-card {
  width: 100%;
  max-width: 380px;
}
.auth-head {
  margin-bottom: var(--sp-5);
}
.eyebrow {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  letter-spacing: 0.16em;
  color: var(--amber-700);
  margin-bottom: var(--sp-2);
}
.auth-head h2 {
  font-family: var(--font-serif);
  font-size: var(--fs-xl);
  font-weight: 600;
}
.auth-form :deep(.el-form-item) {
  margin-bottom: var(--sp-3);
}
.auth-form :deep(.el-input__wrapper) {
  padding: 4px 14px;
  background: var(--paper-raised);
}
.submit {
  width: 100%;
  height: 46px;
  margin-top: var(--sp-3);
  font-size: var(--fs-base);
  letter-spacing: 0.3em;
  text-indent: 0.3em;
}
.auth-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  margin-top: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--text-muted);
}
/* 三条说明合成一块：分开三个色块太抢眼，聚在一起读起来也更像"注册须知" */
.account-notes {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: var(--sp-4);
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--amber-100);
}
.note-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--amber-700);
}
.note-line .el-icon {
  margin-top: 2px;
  flex-shrink: 0;
}
.auth-meta {
  margin-top: var(--sp-6);
  font-size: var(--fs-xs);
  letter-spacing: 0.1em;
  color: var(--text-muted);
}

/* ---------------- 入场编排 ---------------- */
@media (prefers-reduced-motion: no-preference) {
  .brand-top,
  .brand-statement,
  .brand-steps,
  .auth-card,
  .auth-meta {
    animation: rise var(--dur-slow) var(--ease-out) both;
  }
  .brand-statement {
    animation-delay: 80ms;
  }
  .brand-steps {
    animation-delay: 180ms;
  }
  .auth-card {
    animation-delay: 60ms;
  }
  .auth-meta {
    animation-delay: 240ms;
  }
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(22px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 960px) {
  /* 必须用 minmax(0, 1fr)：1fr 的自动最小尺寸会被输入框长 placeholder 撑开，
     导致整条栅格轨道溢出视口、表单被裁切 */
  .auth {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: 100%;
  }
  .auth-brand {
    padding: var(--sp-6) var(--sp-5);
    gap: var(--sp-5);
  }
  .brand-steps {
    display: none;
  }
  .brand-statement h1 {
    font-size: 26px;
  }
  .auth-panel {
    padding: var(--sp-6) var(--sp-5) var(--sp-8);
  }
  .auth-meta {
    display: none;
  }
}
</style>
