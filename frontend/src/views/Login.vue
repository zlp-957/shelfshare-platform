<template>
  <div class="auth">
    <!-- 左侧品牌区：深墨绿 + 书脊竖线纹理 -->
    <section class="auth-brand">
      <div class="brand-top">
        <div class="mark">
          <el-icon :size="20"><Reading /></el-icon>
        </div>
        <span class="brand-name">书屿</span>
      </div>

      <div class="brand-statement">
        <div class="rule"></div>
        <h1>把每一本书<br />交到需要它的人手里</h1>
        <p class="brand-sub">共享借阅 · 押金担保 · 到馆核销 · 在线支付</p>
      </div>

      <ul class="brand-features">
        <li><span class="idx">01</span><span>免费借阅，每人最多 5 本</span></li>
        <li><span class="idx">02</span><span>借阅期限 30 天，逾期自动转购</span></li>
        <li><span class="idx">03</span><span>库存不足可预约排队，到货自动通知</span></li>
      </ul>
    </section>

    <!-- 右侧表单区 -->
    <section class="auth-panel">
      <div class="auth-card">
        <header class="auth-head">
          <p class="eyebrow">SHELFSHARE · 读者与管理员</p>
          <h2>登录账号</h2>
        </header>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" class="auth-form">
          <el-form-item prop="account">
            <el-input
              v-model="form.account"
              placeholder="账号（读者 R+8位数字 / 管理员 admin）"
              clearable
            >
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              show-password
              @keyup.enter="handleLogin"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <UBlobButton
              text="登 录"
              class="submit"
              :loading="loading"
              @click="handleLogin"
            />
          </el-form-item>
        </el-form>

        <div class="auth-foot">
          <span>还没有账号？</span>
          <el-link type="primary" :underline="false" @click="$router.push('/register')">
            立即注册
          </el-link>
          <span class="foot-dot">·</span>
          <el-link :underline="false" @click="openForgot">忘记密码</el-link>
        </div>
      </div>

      <p class="auth-meta">广东财经大学 · 图书借阅管理</p>

      <!-- 忘记密码：先提交申请 → 管理员电话核实后给重置码 → 自己设新密码 -->
      <el-dialog
        v-model="forgotVisible"
        title="找回密码"
        width="460px"
        :close-on-click-modal="false"
        class="forgot-dialog"
      >
        <el-tabs v-model="forgotTab">
          <!-- 第一步：提交申请 -->
          <el-tab-pane label="提交申请" name="apply">
            <p class="forgot-tip">
              填写注册时用的姓名和手机号——系统用这两项核对你的身份，必须与注册信息一致。
              核实后工作人员会用这个号码联系你，并提供一个重置码。
            </p>
            <el-form :model="applyForm" :rules="applyRules" ref="applyFormRef" label-width="88px">
              <el-form-item label="真实姓名" prop="name">
                <el-input v-model="applyForm.name" placeholder="与注册时填写的真实姓名一致" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="applyForm.phone" placeholder="注册时填写的手机号" maxlength="11" />
              </el-form-item>
            </el-form>
            <el-button type="primary" class="forgot-submit" :loading="applyLoading" @click="doApply">
              提交申请
            </el-button>
            <p v-if="applied" class="forgot-done">
              申请已提交。拿到重置码后，请切换到「使用重置码」。
            </p>
          </el-tab-pane>

          <!-- 第二步：用重置码改密 -->
          <el-tab-pane label="使用重置码" name="reset">
            <p class="forgot-tip">
              输入工作人员告诉你的 6 位重置码，设置新密码。重置码 30 分钟内有效。
            </p>
            <el-form :model="resetForm" :rules="resetRules" ref="resetFormRef" label-width="88px">
              <el-form-item label="账号" prop="account">
                <el-input v-model="resetForm.account" placeholder="如 R20260001" />
              </el-form-item>
              <el-form-item label="重置码" prop="code">
                <el-input v-model="resetForm.code" placeholder="6 位数字" maxlength="6" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="至少6位" />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="再次输入" />
              </el-form-item>
            </el-form>
            <el-button type="primary" class="forgot-submit" :loading="resetLoading" @click="doReset">
              设置新密码
            </el-button>
          </el-tab-pane>
        </el-tabs>
      </el-dialog>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, applyPasswordReset, resetPasswordByCode } from '../api'
import { useUserStore } from '../stores/user'
import UBlobButton from '../components/ui/UBlobButton.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  // 注册成功后跳过来会带 ?account=xxx，账号是系统分配的，直接帮他填上
  account: route.query.account || '',
  password: ''
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await login(form)
      userStore.setLogin(res.data.token, {
        userId: res.data.userId,
        account: res.data.account,
        name: res.data.name,
        role: res.data.role
      })
      ElMessage.success('登录成功')
      router.push(res.data.role === 1 ? '/admin' : '/reader')
    } catch (e) {
      // 错误已在拦截器提示
    } finally {
      loading.value = false
    }
  })
}

// ==================== 忘记密码 ====================

const forgotVisible = ref(false)
const forgotTab = ref('apply')
const applied = ref(false)

const applyFormRef = ref()
const applyLoading = ref(false)
const applyForm = reactive({ name: '', phone: '' })
const applyRules = {
  name: [{ required: true, message: '请填写真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号应为 11 位数字', trigger: 'blur' }
  ]
}

const resetFormRef = ref()
const resetLoading = ref(false)
const resetForm = reactive({ account: '', code: '', newPassword: '', confirmPassword: '' })
const resetRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入重置码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '重置码为 6 位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetForm.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openForgot() {
  forgotVisible.value = true
  forgotTab.value = 'apply'
  applied.value = false
  applyForm.name = ''
  applyForm.phone = ''
  resetForm.account = form.account || ''
  resetForm.code = ''
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
}

function doApply() {
  applyFormRef.value.validate(async (valid) => {
    if (!valid) return
    applyLoading.value = true
    try {
      await applyPasswordReset({ name: applyForm.name, phone: applyForm.phone })
      applied.value = true
      ElMessage.success('申请已提交，请等待工作人员电话联系')
    } catch (e) {
      // 拦截器已提示（例如姓名与手机号不匹配）
    } finally {
      applyLoading.value = false
    }
  })
}

function doReset() {
  resetFormRef.value.validate(async (valid) => {
    if (!valid) return
    resetLoading.value = true
    try {
      await resetPasswordByCode({
        account: resetForm.account,
        code: resetForm.code,
        newPassword: resetForm.newPassword
      })
      ElMessage.success('密码已重置，请用新密码登录')
      // 把账号带回登录框，省得他自己再输一遍
      form.account = resetForm.account
      form.password = ''
      forgotVisible.value = false
    } catch (e) {
      // 拦截器已提示
    } finally {
      resetLoading.value = false
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
/* 书脊竖线纹理 */
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
/* 琥珀氛围光 */
.auth-brand::after {
  content: "";
  position: absolute;
  right: -14%;
  top: -18%;
  width: 60%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(200, 134, 60, 0.28),
    transparent 65%
  );
  pointer-events: none;
}

.brand-top,
.brand-statement,
.brand-features {
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
  font-size: clamp(30px, 3.4vw, 46px);
  font-weight: 600;
  line-height: 1.24;
  letter-spacing: 0.01em;
  color: var(--text-inverse);
}
.brand-sub {
  margin-top: var(--sp-4);
  font-size: var(--fs-sm);
  letter-spacing: 0.14em;
  color: rgba(244, 242, 237, 0.62);
}

.brand-features {
  list-style: none;
  display: grid;
  gap: var(--sp-3);
  border-top: 1px solid rgba(244, 242, 237, 0.14);
  padding-top: var(--sp-5);
  max-width: 420px;
}
.brand-features li {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
  color: rgba(244, 242, 237, 0.78);
}
.brand-features .idx {
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
  margin-bottom: var(--sp-6);
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
  margin-bottom: var(--sp-4);
}
.auth-form :deep(.el-input__wrapper) {
  padding: 4px 14px;
  background: var(--paper-raised);
}
.submit {
  width: 100%;
  height: 46px;
  margin-top: var(--sp-2);
  font-size: var(--fs-base);
  letter-spacing: 0.3em;
  text-indent: 0.3em;
}
.auth-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  margin-top: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--text-muted);
}
.foot-dot {
  color: var(--text-muted);
  margin: 0 2px;
}
.forgot-tip {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-muted);
  margin: 0 0 16px;
}
.forgot-submit {
  width: 100%;
}
.forgot-done {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: var(--r-sm);
  background: var(--amber-100);
  color: var(--amber-700);
  font-size: 12px;
  line-height: 1.6;
}
.auth-meta {
  margin-top: var(--sp-7);
  font-size: var(--fs-xs);
  letter-spacing: 0.1em;
  color: var(--text-muted);
}

/* ---------------- 入场编排 ---------------- */
@media (prefers-reduced-motion: no-preference) {
  .brand-top,
  .brand-statement,
  .brand-features,
  .auth-card,
  .auth-meta {
    animation: rise var(--dur-slow) var(--ease-out) both;
  }
  .brand-statement {
    animation-delay: 80ms;
  }
  .brand-features {
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
  }
  .auth-brand {
    padding: var(--sp-6) var(--sp-5);
    gap: var(--sp-6);
  }
  .brand-features {
    display: none;
  }
  .brand-statement h1 {
    font-size: 28px;
  }
  .auth-meta {
    display: none;
  }
}
</style>
