<script setup lang="ts">
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
      <div class="login__coordinates">
        <span>DAYAN / ADMIN</span>
        <span>{{ new Date().getFullYear() }}</span>
      </div>
      <div class="login__brand">
        <div
          class="login__mark"
          aria-hidden="true"
        >
          <span />
          <span />
        </div>
        <p>{{ t('app.name') }}</p>
        <h1>{{ t('login.tagline') }}</h1>
      </div>
      <p class="login__environment">
        <span aria-hidden="true" />
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
          <p>{{ t('login.accountAuth') }}</p>
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
  grid-template-columns: minmax(360px, 0.92fr) minmax(460px, 1.08fr);
  color: var(--color-text-primary);
  background: var(--color-surface);
}

.login__identity {
  position: relative;
  display: flex;
  min-height: 100svh;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: clamp(28px, 4vw, 58px);
  color: #e8edf2;
  background-color: #17232b;
  background-image:
    linear-gradient(rgb(255 255 255 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 4%) 1px, transparent 1px);
  background-size: 48px 48px;
}

.login__identity::after {
  position: absolute;
  right: 0;
  bottom: 18%;
  width: 38%;
  height: 1px;
  background: var(--color-accent-soft);
  content: '';
}

.login__coordinates {
  display: flex;
  justify-content: space-between;
  color: #82909b;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.15em;
}

.login__brand {
  position: relative;
  z-index: 1;
  max-width: 520px;
}

.login__brand p {
  margin: 0 0 21px;
  color: #a9b7c0;
  font-size: 12px;
  letter-spacing: 0.15em;
}

.login__brand h1 {
  margin: 0;
  color: #fff;
  font-size: clamp(30px, 4vw, 50px);
  font-weight: 580;
  letter-spacing: -0.045em;
  line-height: 1.22;
}

.login__mark {
  position: absolute;
  top: -71px;
  left: 0;
  width: 46px;
  height: 35px;
}

.login__mark span {
  position: absolute;
  width: 29px;
  height: 12px;
  border: 2px solid var(--color-accent-soft);
  border-left: 0;
  border-radius: 0 20px 20px 0;
  transform: rotate(-18deg);
}

.login__mark span:last-child {
  right: 0;
  bottom: 0;
  transform: rotate(18deg) scaleX(-1);
}

.login__environment {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  color: #82909b;
  font-size: 11px;
}

.login__environment span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-accent-soft);
  box-shadow: 0 0 0 4px rgb(112 185 199 / 10%);
}

.login__form-area {
  position: relative;
  display: flex;
  min-height: 100svh;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px clamp(28px, 8vw, 120px) 30px;
}

.login__lang {
  position: absolute;
  top: 18px;
  right: clamp(18px, 4vw, 42px);
}

.login-form {
  width: min(360px, 100%);
}

.login-form header {
  margin-bottom: 38px;
}

.login-form header p {
  margin: 0 0 8px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.14em;
}

.login-form h2 {
  margin: 0;
  font-size: 30px;
  font-weight: 650;
  letter-spacing: -0.04em;
}

.login-form header span {
  display: block;
  margin-top: 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.field {
  margin-bottom: 21px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  color: var(--color-text-primary);
  font-size: 12px;
  font-weight: 580;
}

.field input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: 5px;
  outline: 0;
  color: var(--color-text-primary);
  background: #fff;
  transition: border-color 140ms ease, box-shadow 140ms ease;
}

.field input:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgb(18 116 138 / 9%);
}

.field input::placeholder {
  color: #a1adb5;
}

.login-form__error {
  margin: -4px 0 17px;
  color: var(--color-danger);
  font-size: 12px;
  line-height: 1.55;
}

.login-form__submit {
  display: flex;
  width: 100%;
  height: 42px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  margin-top: 8px;
  cursor: pointer;
  border: 0;
  border-radius: 5px;
  color: #fff;
  background: var(--color-accent);
  font-size: 13px;
  font-weight: 600;
  transition: background-color 140ms ease, transform 140ms ease;
}

.login-form__submit:hover:not(:disabled) {
  background: #0d6679;
}

.login-form__submit:active:not(:disabled) {
  transform: translateY(1px);
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
  width: min(360px, 100%);
  margin: auto 0 0;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 780px) {
  .login {
    grid-template-columns: 1fr;
  }

  .login__identity {
    min-height: 210px;
    justify-content: flex-end;
    padding: 28px 24px 31px;
  }

  .login__coordinates,
  .login__environment {
    display: none;
  }

  .login__brand p {
    margin-bottom: 10px;
  }

  .login__brand h1 {
    max-width: 420px;
    font-size: clamp(25px, 8vw, 34px);
  }

  .login__mark {
    top: -48px;
    transform: scale(0.75);
    transform-origin: left bottom;
  }

  .login__form-area {
    min-height: calc(100svh - 210px);
    justify-content: flex-start;
    padding: 42px 24px 24px;
  }

  .login__footnote {
    margin-top: 46px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-form__spinner {
    animation: none;
  }
}
</style>
