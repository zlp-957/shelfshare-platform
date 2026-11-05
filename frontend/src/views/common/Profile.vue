<template>
  <div class="profile-container">
    <el-row :gutter="20">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>个人信息</span></template>
          <div class="info-display">
            <div class="avatar-big" @click="triggerUpload">
              <el-avatar
                :size="80"
                :src="user?.avatar || undefined"
                style="background: var(--green-700); font-size: 32px"
              >
                {{ !user?.avatar ? user?.name?.charAt(0) : '' }}
              </el-avatar>
              <div class="avatar-hint">点击更换头像</div>
            </div>
            <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFileChange" />
            <div class="info-items">
              <div class="info-item"><span class="label">账号：</span>{{ user?.account }}</div>
              <div class="info-item"><span class="label">姓名：</span>{{ user?.name }}</div>
              <div class="info-item"><span class="label">角色：</span>{{ user?.role === 1 ? '管理员' : '读者' }}</div>
              <div class="info-item"><span class="label">手机号：</span>{{ user?.phone || '未填写' }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never" style="margin-bottom: 20px">
          <template #header><span>修改信息</span></template>
          <el-form :model="profileForm" label-width="80px">
            <el-form-item label="姓名">
              <el-input v-model="profileForm.name" placeholder="真实姓名（取书时核对）" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" placeholder="11 位手机号" maxlength="11" />
              <div class="field-tip">用于到书通知，以及忘记密码时核对身份找回账号</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleUpdateProfile">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card shadow="never">
          <template #header><span>修改密码</span></template>
          <el-form :model="pwdForm" label-width="80px">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleUpdatePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserInfo, updateProfile, updatePassword, uploadAvatar, updatePhone } from '../../api'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const user = ref(null)
const profileForm = reactive({ name: '', phone: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const fileInput = ref(null)

const loadUser = async () => {
  const res = await getUserInfo()
  user.value = res.data
  profileForm.name = res.data.name
  profileForm.phone = res.data.phone || ''
  userStore.setUserInfo({ ...userStore.userInfo, name: res.data.name, avatar: res.data.avatar })
}

const triggerUpload = () => {
  fileInput.value?.click()
}

const onFileChange = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  try {
    await uploadAvatar(file)
    ElMessage.success('头像已更新')
    loadUser()
  } catch (err) {
    ElMessage.error(err?.message || '头像上传失败')
  } finally {
    e.target.value = ''
  }
}

const handleUpdateProfile = async () => {
  if (!profileForm.name) {
    ElMessage.warning('姓名不能为空')
    return
  }
  await updateProfile({ name: profileForm.name })
  // 手机号单独更新（可能为空则清空）
  if (profileForm.phone !== (user.value?.phone || '')) {
    await updatePhone({ phone: profileForm.phone })
  }
  ElMessage.success('修改成功')
  loadUser()
}

const handleUpdatePassword = async () => {
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  await updatePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
  ElMessage.success('密码修改成功，请重新登录')
  userStore.logout()
  window.location.href = '/login'
}

onMounted(loadUser)
</script>

<style scoped>
.info-display {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.avatar-big {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.avatar-hint {
  font-size: 12px;
  color: var(--text-muted);
}
.info-items {
  flex: 1;
}
.info-item {
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--text);
}
.info-item .label {
  color: var(--text-muted);
}
/* 手机号用途说明：输入后 placeholder 会消失，这条常驻 */
.field-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-muted);
}
</style>
