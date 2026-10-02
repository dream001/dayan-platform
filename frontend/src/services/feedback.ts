import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { i18n } from '@/i18n'
import type { ApiResponse } from '@/types/api'

const t = i18n.global.t

export function getErrorMessage(error: unknown, fallback: string = t('common.operationIncomplete')) {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return error.response?.data?.message || error.message || fallback
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
