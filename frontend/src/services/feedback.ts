import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ApiResponse } from '@/types/api'

export function getErrorMessage(error: unknown, fallback = '操作未完成，请稍后重试') {
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
  title = '确认操作',
  confirmButtonText = '确认',
) {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText,
      cancelButtonText: '取消',
      type: 'warning',
      autofocus: false,
      closeOnClickModal: false,
    })
    return true
  } catch {
    return false
  }
}
