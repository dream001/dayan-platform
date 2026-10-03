import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { i18n } from '@/i18n'
import type { ApiResponse } from '@/types/api'

const t = i18n.global.t

function validationDetails(data: unknown) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return ''
  return Object.entries(data)
    .flatMap(([field, messages]) => Array.isArray(messages)
      ? messages.map((message) => `${field}: ${String(message)}`)
      : [])
    .join('; ')
}

export function getErrorMessage(error: unknown, fallback: string = t('common.operationIncomplete')) {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    const response = error.response?.data
    return validationDetails(response?.data) || response?.message || error.message || fallback
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}

export function notifyError(error: unknown, fallback?: string) {
  ElMessage.error({
    message: getErrorMessage(error, fallback),
    duration: 4_000,
    showClose: true,
  })
}

export async function confirmAction(
  message: string,
  title: string = t('common.confirm'),
  confirmButtonText: string = t('common.confirm'),
) {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText,
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      autofocus: false,
      closeOnClickModal: false,
    })
    return true
  } catch {
    return false
  }
}
