<script setup lang="ts">
import { ArrowRight, Cpu, Lock } from '@element-plus/icons-vue'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { getErrorMessage } from '@/services/feedback'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: '', password: '' })
const submitting = ref(false)
const errorMessage = ref('')

const canSubmit = computed(() =>
  form.username.trim().length > 0 && form.password.length > 0 && !submitting.value,
)

async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await auth.signIn({
      username: form.username.trim(),
      password: form.password,
    })
    await router.replace({ name: 'workspace' })
  } catch (error) {
    errorMessage.value = getErrorMessage(error, t('login.invalidCredentials'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login">
    <section
      class="login__identity"
      :aria-label="t('app.name')"
    >
      <div class="login__veil" />
      <header class="login__masthead">
        <div
          class="login__emblem"
          aria-hidden="true"
        >
          <el-icon :size="21">
            <Cpu />
          </el-icon>
        </div>
        <div>
          <strong>{{ t('app.name') }}</strong>
          <span>DAYAN EMBODIED INTELLIGENCE</span>
        </div>
      </header>

      <div class="login__brand">
        <p>{{ t('login.systemMode') }} · {{ new Date().getFullYear() }}</p>
        <h1>{{ t('app.name') }}</h1>
        <div class="login__brand-rule" />
        <h2>{{ t('login.tagline') }}</h2>
      </div>

      <p class="login__environment">
        <el-icon :size="15">
          <Lock />
        </el-icon>
        {{ t('login.secureChannel') }}
      </p>
    </section>

    <section
      class="login__form-area"
      aria-labelledby="login-title"
    >
      <LanguageSwitcher class="login__lang" />
      <form
        class="login-form"
        @submit.prevent="handleSubmit"
      >
        <header>
          <p><span />{{ t('login.accountAuth') }}</p>
          <h2 id="login-title">
            {{ t('login.title') }}
          </h2>
          <span>{{ t('login.subtitle') }}</span>
        </header>

        <div class="field">
          <label for="username">{{ t('login.username') }}</label>
          <input
            id="username"
            v-model="form.username"
            name="username"
            type="text"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
            required
            autofocus
            :placeholder="t('login.usernamePlaceholder')"
          >
        </div>

        <div class="field">
          <label for="password">{{ t('login.password') }}</label>
          <input
            id="password"
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
            :placeholder="t('login.passwordPlaceholder')"
          >
        </div>

        <p
          v-if="errorMessage"
          class="login-form__error"
          role="alert"
        >
          {{ errorMessage }}
        </p>

        <button
          class="login-form__submit"
          type="submit"
          :disabled="!canSubmit"
        >
          <span
            v-if="submitting"
            class="login-form__spinner"
            aria-hidden="true"
          />
          <el-icon
            v-else
            :size="17"
          >
            <ArrowRight />
          </el-icon>
          {{ submitting ? t('login.submitting') : t('login.submit') }}
        </button>
      </form>

      <p class="login__footnote">
        {{ t('login.footnote') }}
      </p>
    </section>
  </main>
</template>

<style scoped>
.login {
  display: grid;
  min-height: 100svh;
  grid-template-columns: minmax(480px, 1.12fr) minmax(520px, 0.88fr);
  color: #152126;
  background: #f2f5f4;
}

.login__identity {
  position: relative;
  display: flex;
  min-height: 100svh;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: clamp(32px, 4.2vw, 68px);
  color: #eff8f7;
  background-color: #0a1114;
  background-image: url("https://copilot-cn.bytedance.net/api/ide/v1/text_to_image?prompt=cinematic%20realistic%20photography%20of%20an%20advanced%20humanoid%20robot%20carefully%20handling%20a%20precision%20component%20at%20a%20workbench%20inside%20a%20world-class%20Chinese%20embodied%20intelligence%20robotics%20laboratory%2C%20visible%20robotic%20hands%20and%20sensors%2C%20physical%20interaction%20with%20real%20objects%2C%20dark%20graphite%20industrial%20environment%2C%20restrained%20cyan%20status%20lights%2C%20natural%20cinematic%20lighting%2C%20premium%20technology%20editorial%20composition%2C%20calm%20negative%20space%20for%20white%20text%20on%20the%20left%2C%20no%20logos%2C%20no%20words%2C%20no%20interface%20overlays&image_size=portrait_4_3");
  background-position: center;
  background-size: cover;
}

.login__veil {
  position: absolute;
  inset: 0;
  background: rgb(4 13 17 / 54%);
  pointer-events: none;
}

.login__masthead {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.login__masthead > div:last-child {
  display: grid;
  gap: 4px;
}

.login__masthead strong {
  color: #f1f8f7;
  font-size: 15px;
  font-weight: 600;
}

.login__masthead span {
  color: rgb(218 239 237 / 52%);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0;
}

.login__emblem {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  border: 1px solid rgb(91 205 202 / 58%);
  color: #73d6d2;
  background: rgb(7 28 32 / 52%);
}

.login__brand {
  position: relative;
  z-index: 1;
  width: min(640px, 92%);
  margin-bottom: 4vh;
}

.login__brand p {
  margin: 0 0 22px;
  color: #73d6d2;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0;
}

.login__brand h1 {
  margin: 0;
  color: #f4fbfa;
  font-size: clamp(46px, 4.9vw, 76px);
  font-weight: 650;
  letter-spacing: 0;
  line-height: 1.1;
  text-shadow: 0 4px 28px rgb(0 0 0 / 24%);
}

.login__brand-rule {
  width: 72px;
  height: 1px;
  margin: 27px 0 24px;
  background: #59c5c2;
}

.login__brand h2 {
  max-width: 470px;
  margin: 0;
  color: rgb(232 246 244 / 78%);
  font-size: clamp(18px, 1.7vw, 25px);
  font-weight: 420;
  letter-spacing: 0;
  line-height: 1.65;
}

.login__environment {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: rgb(220 237 235 / 62%);
  font-size: 11px;
}

.login__form-area {
  position: relative;
  display: flex;
  min-height: 100svh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 72px clamp(36px, 7vw, 112px) 36px;
  border-left: 1px solid #cbd7d5;
  background-color: #f2f5f4;
}

.login__lang {
  position: absolute;
  top: 28px;
  right: clamp(24px, 4vw, 46px);
  color: #536368;
}

.login-form {
  width: min(430px, 100%);
  animation: form-enter 560ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.login-form header {
  margin-bottom: 46px;
}

.login-form header p {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 12px;
  color: #167d80;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0;
}

.login-form header p span {
  width: 24px;
  height: 1px;
  margin: 0;
  background: #258f91;
}

.login-form h2 {
  margin: 0;
  color: #152126;
  font-size: 36px;
  font-weight: 680;
  letter-spacing: 0;
  line-height: 1.25;
}

.login-form header span {
  display: block;
  margin-top: 13px;
  color: #66757a;
  font-size: 13px;
  line-height: 1.7;
}

.field {
  margin-bottom: 24px;
}

.field label {
  display: block;
  margin-bottom: 10px;
  color: #27363b;
  font-size: 12px;
  font-weight: 650;
}

.field input {
  width: 100%;
  height: 52px;
  padding: 0 16px;
  border: 1px solid #c7d2d1;
  border-radius: 2px;
  outline: 0;
  color: #152126;
  background: rgb(255 255 255 / 72%);
  font-size: 15px;
  transition: border-color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.field input:focus {
  border-color: #16868a;
  background: #fff;
  box-shadow: 0 0 0 3px rgb(22 134 138 / 10%);
}

.field input::placeholder {
  color: #969c98;
}

.login-form__error {
  margin: -5px 0 18px;
  color: #9d2f2f;
  font-size: 12px;
  line-height: 1.55;
}

.login-form__submit {
  position: relative;
  display: flex;
  width: 100%;
  height: 52px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin-top: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #123d42;
  border-radius: 2px;
  color: #f0fbfa;
  background: #164b50;
  font-size: 13px;
  font-weight: 600;
  transition: background-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.login-form__submit:hover:not(:disabled) {
  background: #0f666b;
  box-shadow: 0 10px 24px rgb(15 102 107 / 18%);
  transform: translateY(-1px);
}

.login-form__submit:active:not(:disabled) {
  box-shadow: none;
  transform: translateY(0);
}

.login-form__submit:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.login-form__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgb(255 255 255 / 35%);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

.login__footnote {
  width: min(430px, 100%);
  margin: auto 0 0;
  padding-top: 24px;
  border-top: 1px solid #d4dddc;
  color: #819094;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes form-enter {
  from {
    opacity: 0;
    transform: translateY(16px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 980px) {
  .login {
    grid-template-columns: 1fr;
  }

  .login__identity {
    min-height: 280px;
    justify-content: flex-end;
    padding: 24px;
    background-position: center 45%;
  }

  .login__masthead,
  .login__environment {
    display: none;
  }

  .login__brand p {
    margin-bottom: 12px;
  }

  .login__brand {
    width: 100%;
    margin: 0;
  }

  .login__brand h1 {
    font-size: clamp(37px, 9vw, 56px);
  }

  .login__brand-rule {
    margin: 17px 0 13px;
  }

  .login__brand h2 {
    font-size: 17px;
    line-height: 1.5;
  }

  .login__form-area {
    min-height: calc(100svh - 280px);
    justify-content: flex-start;
    padding: 48px 24px 24px;
    border-left: 0;
    border-top: 1px solid #cbd7d5;
  }

  .login__footnote {
    margin-top: 46px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-form,
  .login-form__spinner {
    animation: none;
  }
}
</style>
