<script setup lang="ts">
import { Delete, Download, Refresh, Upload, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  deleteFile,
  downloadFile,
  getFilePreview,
  getFiles,
  uploadFile,
} from '@/services/admin'
import { confirmAction, getErrorMessage, notifyError } from '@/services/feedback'
import type { StoredFile } from '@/types/admin'
import { formatBytes, formatDateTime } from '@/utils/format'

const loading = ref(false)
const error = ref('')
const files = ref<StoredFile[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })
const input = ref<HTMLInputElement>()
const dragging = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewFile = ref<StoredFile | null>(null)
const previewUrl = ref('')
const previewExpiresAt = ref('')
const isImagePreview = computed(() => previewFile.value?.contentType.startsWith('image/'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getFiles({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
    })
    files.value = page.items
    total.value = page.total
  } catch (reason) {
    error.value = getErrorMessage(reason, '文件列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function pickFile() {
  input.value?.click()
}

async function upload(filesToUpload: FileList | File[]) {
  const file = filesToUpload[0]
  if (!file || uploading.value) return
  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadFile(file, (value) => {
      uploadProgress.value = value
    })
    ElMessage.success(`“${file.name}”上传完成`)
    query.page = 1
    await load()
  } catch (reason) {
    notifyError(reason, '文件上传失败')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
    if (input.value) input.value.value = ''
  }
}

function handleDrop(event: DragEvent) {
  dragging.value = false
  if (event.dataTransfer?.files.length) void upload(event.dataTransfer.files)
}

async function preview(file: StoredFile) {
  previewFile.value = file
  previewOpen.value = true
  previewLoading.value = true
  previewUrl.value = ''
  try {
    const result = await getFilePreview(file.id)
    previewUrl.value = result.url
    previewExpiresAt.value = result.expiresAt
  } catch (reason) {
    previewOpen.value = false
    notifyError(reason, '文件预览链接获取失败')
  } finally {
    previewLoading.value = false
  }
}

async function download(file: StoredFile) {
  try {
    const blob = await downloadFile(file.id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = file.originalName
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (reason) {
    notifyError(reason, '文件下载失败')
  }
}

async function remove(file: StoredFile) {
  const confirmed = await confirmAction(
    `永久删除文件“${file.originalName}”？此操作不可撤销。`,
    '删除文件',
    '删除',
  )
  if (!confirmed) return
  try {
    await deleteFile(file.id)
    ElMessage.success('文件已删除')
    if (files.value.length === 1 && query.page > 1) query.page -= 1
    await load()
  } catch (reason) {
    notifyError(reason, '文件删除失败')
  }
}

onMounted(load)
onBeforeUnmount(() => {
  previewUrl.value = ''
})
</script>

<template>
  <section class="admin-page">
    <PageHeader
      title="文件管理"
      eyebrow="Files"
      description="上传、查找、预览与下载存储文件。"
    >
      <template #actions>
        <el-button
          v-permission="'file:upload'"
          type="primary"
          :icon="Upload"
          :loading="uploading"
          @click="pickFile"
        >
          选择文件
        </el-button>
      </template>
    </PageHeader>

    <input
      ref="input"
      class="visually-hidden"
      type="file"
      @change="($event) => upload(($event.target as HTMLInputElement).files ?? [])"
    >

    <button
      v-permission="'file:upload'"
      class="upload-zone"
      :class="{ 'upload-zone--dragging': dragging }"
      type="button"
      :disabled="uploading"
      @click="pickFile"
      @dragenter.prevent="dragging = true"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="handleDrop"
    >
      <el-icon :size="21">
        <Upload />
      </el-icon>
      <span>
        <strong>{{ uploading ? `正在上传 ${uploadProgress}%` : '拖拽文件到此处，或点击选择' }}</strong>
        <small>文件大小和类型由服务端策略校验</small>
      </span>
      <span
        v-if="uploading"
        class="upload-progress"
        :style="{ width: `${uploadProgress}%` }"
      />
    </button>

    <form
      class="filter-bar"
      @submit.prevent="search"
    >
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="按文件名查找"
      />
      <el-button
        type="primary"
        native-type="submit"
      >
        查询
      </el-button>
      <el-button
        @click="query.keyword = ''; search()"
      >
        重置
      </el-button>
      <el-tooltip content="刷新列表">
        <el-button
          :icon="Refresh"
          circle
          aria-label="刷新列表"
          @click="load"
        />
      </el-tooltip>
    </form>

    <StatePanel
      v-if="loading && !files.length"
      state="loading"
    />
    <StatePanel
      v-else-if="error && !files.length"
      state="error"
      title="文件列表加载失败"
      :description="error"
      @retry="load"
    />
    <StatePanel
      v-else-if="!files.length"
      state="empty"
      title="未找到文件"
      description="上传文件或调整查找条件。"
    />
    <template v-else>
      <div class="table-shell">
        <el-table
          v-loading="loading"
          :data="files"
          row-key="id"
        >
          <el-table-column
            prop="originalName"
            label="文件名"
            min-width="260"
            fixed="left"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <strong class="file-name">{{ row.originalName }}</strong>
            </template>
          </el-table-column>
          <el-table-column
            prop="contentType"
            label="类型"
            min-width="190"
            show-overflow-tooltip
          />
          <el-table-column
            label="大小"
            width="100"
            align="right"
          >
            <template #default="{ row }">
              {{ formatBytes(row.sizeBytes) }}
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.status === 'AVAILABLE' ? 'success' : 'info'"
                effect="plain"
              >
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="上传时间"
            min-width="165"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="138"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="table-actions">
                <el-tooltip content="预览文件">
                  <el-button
                    v-permission="'file:preview'"
                    :icon="View"
                    circle
                    text
                    aria-label="预览文件"
                    @click="preview(row)"
                  />
                </el-tooltip>
                <el-tooltip content="下载文件">
                  <el-button
                    v-permission="'file:download'"
                    :icon="Download"
                    circle
                    text
                    aria-label="下载文件"
                    @click="download(row)"
                  />
                </el-tooltip>
                <el-tooltip content="删除文件">
                  <el-button
                    v-permission="'file:delete'"
                    :icon="Delete"
                    circle
                    text
                    type="danger"
                    aria-label="删除文件"
                    @click="remove(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </div>
    </template>

    <el-dialog
      v-model="previewOpen"
      :title="previewFile?.originalName ?? '文件预览'"
      width="min(920px, 92vw)"
      top="5vh"
      destroy-on-close
    >
      <div
        v-loading="previewLoading"
        class="preview-pane"
      >
        <img
          v-if="previewUrl && isImagePreview"
          :src="previewUrl"
          :alt="previewFile?.originalName"
        >
        <iframe
          v-else-if="previewUrl"
          :src="previewUrl"
          title="文件预览"
        />
      </div>
      <template #footer>
        <span class="preview-expiry">
          链接有效期至 {{ formatDateTime(previewExpiresAt) }}
        </span>
        <el-button @click="previewOpen = false">
          关闭
        </el-button>
        <el-button
          v-if="previewFile"
          v-permission="'file:download'"
          type="primary"
          :icon="Download"
          @click="download(previewFile)"
        >
          下载
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.visually-hidden {
  position: fixed;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.upload-zone {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 88px;
  align-items: center;
  gap: 15px;
  margin-top: 22px;
  overflow: hidden;
  padding: 18px 22px;
  cursor: pointer;
  border: 1px dashed var(--color-border-strong);
  border-radius: 6px;
  color: var(--color-accent);
  text-align: left;
  background: rgb(255 255 255 / 65%);
  transition: border-color 140ms ease, background-color 140ms ease;
}

.upload-zone:hover,
.upload-zone--dragging {
  border-color: var(--color-accent);
  background: #f0f7f8;
}

.upload-zone:disabled {
  cursor: wait;
}

.upload-zone > span {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.upload-zone strong {
  color: var(--color-text-primary);
  font-size: 13px;
}

.upload-zone small {
  color: var(--color-text-muted);
  font-size: 11px;
}

.upload-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  background: var(--color-accent);
  transition: width 120ms linear;
}

.file-name {
  font-weight: 600;
}

.preview-pane {
  display: grid;
  min-height: 55vh;
  place-items: center;
  overflow: auto;
  background: #edf1f3;
}

.preview-pane img {
  display: block;
  max-width: 100%;
  max-height: 65vh;
}

.preview-pane iframe {
  width: 100%;
  height: 65vh;
  border: 0;
  background: #fff;
}

.preview-expiry {
  margin-right: auto;
  color: var(--color-text-muted);
  font-size: 11px;
}

:deep(.el-dialog__footer) {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 520px) {
  .preview-expiry {
    display: none;
  }
}
</style>
