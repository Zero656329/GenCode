<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { DEFAULT_TENANT_ID, TITLE } from '@/settings'

interface LoginFormData {
  tenantId: string
  username: string
  password: string
  captchaCode: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const captcha = ref<CaptchaResult>({ captchaId: '', image: '' })
const formState = reactive<LoginFormData>({
  tenantId: DEFAULT_TENANT_ID,
  username: '',
  password: '',
  captchaCode: ''
})

const rules: Record<string, Rule[]> = {
  tenantId: [{ required: true, message: '请输入租户编号', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha(): Promise<void> {
  try {
    captcha.value = await getCaptcha()
  } catch {
    captcha.value = { captchaId: '', image: '' }
  }
}

async function handleLogin(): Promise<void> {
  loading.value = true
  try {
    await userStore.login({
      tenantId: formState.tenantId,
      username: formState.username,
      password: formState.password,
      captchaId: captcha.value.captchaId,
      captchaCode: formState.captchaCode
    })
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    formState.captchaCode = ''
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">{{ TITLE }}</h2>
      <a-form :model="formState" :rules="rules" layout="vertical" @finish="handleLogin">
        <a-form-item label="租户编号" name="tenantId">
          <a-input v-model:value="formState.tenantId" placeholder="请输入租户编号" />
        </a-form-item>
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="formState.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="formState.password" placeholder="请输入密码" />
        </a-form-item>
        <a-form-item label="验证码" name="captchaCode">
          <div class="captcha-row">
            <a-input v-model:value="formState.captchaCode" placeholder="请输入验证码" />
            <img
              v-if="captcha.image"
              :src="captcha.image"
              class="captcha-img"
              title="点击刷新"
              alt="验证码"
              @click="refreshCaptcha"
            />
          </div>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="loading">登 录</a-button>
        </a-form-item>
      </a-form>
      <div class="login-tip">默认账号：admin / admin123（租户 000000）</div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0eaff 0%, #f5f7fa 100%);
}

.login-card {
  width: 380px;
  padding: 32px 32px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 18px rgba(31, 56, 88, 0.12);
}

.login-title {
  text-align: center;
  margin-bottom: 24px;
  font-size: 20px;
  color: rgba(0, 0, 0, 0.88);
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.captcha-row .ant-input {
  flex: 1;
}

.captcha-img {
  width: 100px;
  height: 38px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid #d9d9d9;
}

.login-tip {
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  padding-bottom: 8px;
}
</style>
