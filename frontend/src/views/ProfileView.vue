<script setup lang="ts">
import { computed, reactive, ref, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { changePassword, updateProfile } from '@/services/admin'
import { notifyError } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const profileSaving = ref(false)
const passwordSaving = ref(false)
const profileForm = reactive({ displayName: '', email: '', phone: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const user = computed(() => auth.profile?.user)

watchEffect(() => {
  if (!user.value) return
  profileForm.displayName = user.value.displayName
  profileForm.email = user.value.email ?? ''
  profileForm.phone = user.value.phone ?? ''
})

async function saveProfile() {
  if (!profileForm.displayName.trim()) return
  profileSaving.value = true
  try {
    await updateProfile({
      displayName: profileForm.displayName.trim(),
      email: profileForm.email.trim(),
      phone: profileForm.phone.trim(),
    })
    await auth.fetchProfile()
    ElMessage.success('个人资料已更新')
  } catch (error) {
    notifyError(error, '个人资料更新失败')
  } finally {
    profileSaving.value = false
  }
}

async function savePassword() {
  if (passwordForm.newPassword.length < 12) {
    ElMessage.warning('新密码至少 12 个字符')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已修改，请重新登录')
    try {
      await auth.signOut()
    } finally {
      await router.replace({ name: 'login' })
    }
  } catch (error) {
    notifyError(error, '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}
</script>

<template>
  <section class="admin-page profile-page">
    <PageHeader
      title="个人中心"
      eyebrow="Account"
      description="维护当前账户资料与登录密码。"
    />

    <div class="profile-layout">
      <aside>
        <span class="profile-avatar">{{ user?.displayName?.slice(0, 1) || '—' }}</span>
        <strong>{{ user?.displayName }}</strong>
        <span>@{{ user?.username }}</span>
        <dl>
          <dt>用户编号</dt>
          <dd>{{ user?.id }}</dd>
          <dt>部门编号</dt>
          <dd>{{ user?.departmentId ?? '未分配' }}</dd>
        </dl>
      </aside>

      <div class="profile-forms">
        <section>
          <header>
            <h2>基本资料</h2>
            <p>邮箱和电话可留空。</p>
          </header>
          <el-form
            label-position="top"
            @submit.prevent="saveProfile"
          >
            <el-form-item
              label="显示名称"
              required
            >
              <el-input
                v-model="profileForm.displayName"
                maxlength="100"
              />
            </el-form-item>
            <div class="field-pair">
              <el-form-item label="邮箱">
                <el-input
                  v-model="profileForm.email"
                  type="email"
                  maxlength="254"
                />
              </el-form-item>
              <el-form-item label="电话">
                <el-input
                  v-model="profileForm.phone"
                  maxlength="32"
                />
              </el-form-item>
            </div>
            <el-button
              type="primary"
              native-type="submit"
              :loading="profileSaving"
            >
              保存资料
            </el-button>
          </el-form>
        </section>

        <section>
          <header>
            <h2>修改密码</h2>
            <p>新密码需为 12–72 个字符，修改后需要重新登录。</p>
          </header>
          <el-form
            label-position="top"
            @submit.prevent="savePassword"
          >
            <el-form-item
              label="当前密码"
              required
            >
              <el-input
                v-model="passwordForm.currentPassword"
                type="password"
                show-password
                autocomplete="current-password"
              />
            </el-form-item>
            <div class="field-pair">
              <el-form-item
                label="新密码"
                required
              >
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  show-password
                  minlength="12"
                  maxlength="72"
                  autocomplete="new-password"
                />
              </el-form-item>
              <el-form-item
                label="确认新密码"
                required
              >
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                />
              </el-form-item>
            </div>
            <el-button
              type="primary"
              native-type="submit"
              :loading="passwordSaving"
            >
              修改密码
            </el-button>
          </el-form>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: 230px minmax(0, 720px);
  gap: clamp(32px, 6vw, 80px);
  padding-top: 30px;
}

.profile-layout aside {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.profile-avatar {
  display: grid;
  width: 58px;
  height: 58px;
  margin-bottom: 15px;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: 50%;
  color: var(--color-accent);
  background: #edf5f6;
  font-size: 20px;
}

.profile-layout aside strong {
  font-size: 16px;
}

.profile-layout aside > span:not(.profile-avatar) {
  margin-top: 4px;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.profile-layout dl {
  width: 100%;
  margin: 24px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
}

.profile-layout dt {
  margin-top: 10px;
  color: var(--color-text-muted);
  font-size: 11px;
}

.profile-layout dd {
  margin: 3px 0 0;
  font-family: var(--font-mono);
  font-size: 12px;
}

.profile-forms {
  display: grid;
  gap: 36px;
}

.profile-forms section {
  padding-bottom: 32px;
  border-bottom: 1px solid var(--color-border);
}

.profile-forms header {
  margin-bottom: 20px;
}

.profile-forms h2 {
  margin: 0;
  font-size: 17px;
}

.profile-forms p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.field-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 760px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .field-pair {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
