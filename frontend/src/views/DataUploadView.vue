<script setup lang="ts">
import {
  Close,
  Document,
  FolderOpened,
  Headset,
  Picture,
  Refresh,
  UploadFilled,
  VideoPause,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatePanel from '@/components/StatePanel.vue'
import {
  cancelDataUpload,
  completeDataUpload,
  createDataUploadSession,
  getDataUploadOptions,
  pauseDataUpload,
  resumeDataUpload,
  uploadDataDirect,
  uploadDataPart,
} from '@/services/admin'
import { getErrorMessage } from '@/services/feedback'
import type {
  DataUploadOptions,
  DataUploadType,
  UploadedDataset,
} from '@/types/admin'
import { formatBytes } from '@/utils/format'

type QueueStatus = 'pending' | 'uploading' | 'paused' | 'success' | 'error' | 'cancelled'

interface UploadItem {
  id: string
  file: File
  status: QueueStatus
  progress: number
  loaded: number
  speed: number
  eta: number
  sessionId: string | null
  dataset: UploadedDataset | null
  error: string
  controller: AbortController | null
  startedAt: number
}

const modes: Array<{
  type: DataUploadType
  label: string
  hint: string
  accept: string
  icon: typeof Document
}> = [
  { type: 'MCAP', label: 'MCAP 文件', hint: '原文件直接入库', accept: '.mcap', icon: Document },
  { type: 'BAG', label: 'BAG 文件', hint: '入库后进入 ROS 转换队列', accept: '.bag', icon: Refresh },
  { type: 'VIDEO', label: '视频文件', hint: 'MP4、WebM', accept: '.mp4,.webm', icon: VideoPlay },
  { type: 'AUDIO', label: '音频文件', hint: 'MP3、WAV、AAC、Ogg', accept: '.mp3,.wav,.aac,.ogg', icon: Headset },
  { type: 'IMAGE', label: '图片文件', hint: 'JPG、PNG 原文件入库', accept: '.jpg,.jpeg,.png', icon: Picture },
  { type: 'HDF5', label: 'HDF5 文件', hint: '需指定机器人类型', accept: '.h5,.hdf5', icon: Document },
  { type: 'LEROBOT', label: 'LeRobot 数据', hint: '选择预打包 tar 文件', accept: '.tar', icon: FolderOpened },
  { type: 'BVH', label: 'BVH 文件', hint: '动作可视化数据', accept: '.bvh', icon: Document },
]

const loading = ref(true)
const error = ref('')
const options = ref<DataUploadOptions | null>(null)
const projectId = ref<number | null>(null)
const storageKey = ref('')
const dataType = ref<DataUploadType>('MCAP')
const robotType = ref('')
const fileInput = ref<HTMLInputElement>()
const dragging = ref(false)
const queue = reactive<UploadItem[]>([])

const selectedMode = computed(() => modes.find((mode) => mode.type === dataType.value) ?? modes[0]!)
const ready = computed(() => projectId.value != null && Boolean(storageKey.value))
const activeUploads = computed(() => queue.filter((item) =>
  ['pending', 'uploading', 'paused'].includes(item.status),
))
const videoCompatible = computed(() => {
  if (dataType.value !== 'VIDEO') return true
  return 'MediaStreamTrackProcessor' in window
    && ('captureStream' in HTMLVideoElement.prototype || 'mozCaptureStream' in HTMLVideoElement.prototype)
})

async function loadOptions() {
  loading.value = true
  error.value = ''
  try {
    options.value = await getDataUploadOptions()
    projectId.value ??= options.value.projects[0]?.id ?? null
    storageKey.value ||= options.value.storages[0]?.key ?? ''
  } catch (reason) {
    error.value = getErrorMessage(reason, '上传配置加载失败')
  } finally {
    loading.value = false
  }
}

function chooseFiles() {
  if (!ready.value) {
    ElMessage.warning(projectId.value == null ? '请先选择项目' : '请选择云存储')
    return
  }
  if (!videoCompatible.value) {
    ElMessage.error('浏览器不兼容，请使用 Chrome 94+ 或 Edge 94+')
    return
  }
  fileInput.value?.click()
}

function enqueue(files: FileList | File[]) {
  if (!ready.value) {
    ElMessage.warning(projectId.value == null ? '请先选择项目' : '请选择云存储')
    return
  }
  for (const file of Array.from(files)) {
    if (file.size === 0) {
      ElMessage.error(`不能上传空文件：${file.name}`)
      continue
    }
    const item: UploadItem = {
      id: crypto.randomUUID(),
      file,
      status: 'pending',
      progress: 0,
      loaded: 0,
      speed: 0,
      eta: 0,
      sessionId: null,
      dataset: null,
      error: '',
      controller: null,
      startedAt: 0,
    }
    queue.unshift(item)
    void start(item)
  }
  if (fileInput.value) fileInput.value.value = ''
}

async function fingerprint(file: File) {
  const source = new TextEncoder().encode(`${file.name}:${file.size}:${file.lastModified}`)
  const digest = await crypto.subtle.digest('SHA-256', source)
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('')
}

function updateProgress(item: UploadItem, loaded: number) {
  item.loaded = Math.min(loaded, item.file.size)
  item.progress = Math.min(100, Math.round((item.loaded / item.file.size) * 100))
  const seconds = Math.max((performance.now() - item.startedAt) / 1000, 0.1)
  item.speed = item.loaded / seconds
  item.eta = item.speed > 0 ? Math.max(0, (item.file.size - item.loaded) / item.speed) : 0
}

async function start(item: UploadItem) {
  if (!options.value || !projectId.value || !storageKey.value) return
  item.status = 'uploading'
  item.error = ''
  item.controller = new AbortController()
  item.startedAt = performance.now()
  try {
    const sourceFingerprint = await fingerprint(item.file)
    if (item.file.size <= options.value.multipartThreshold) {
      item.dataset = await uploadDataDirect(
        {
          projectId: projectId.value,
          storageKey: storageKey.value,
          dataType: dataType.value,
          sourceFingerprint,
          robotType: robotType.value.trim() || undefined,
        },
        item.file,
        (loaded) => updateProgress(item, loaded),
        item.controller.signal,
      )
    } else {
      await uploadMultipart(item, sourceFingerprint)
    }
    updateProgress(item, item.file.size)
    item.status = 'success'
  } catch (reason) {
    if (item.controller?.signal.aborted) return
    item.status = 'error'
    item.error = getErrorMessage(reason, '上传失败')
  } finally {
    item.controller = null
  }
}

async function uploadMultipart(item: UploadItem, sourceFingerprint: string) {
  if (!options.value || !projectId.value) return
  let session = item.sessionId
    ? await resumeDataUpload(item.sessionId)
    : await createDataUploadSession({
        projectId: projectId.value,
        storageKey: storageKey.value,
        dataType: dataType.value,
        fileName: item.file.name,
        contentType: item.file.type || 'application/octet-stream',
        totalSize: item.file.size,
        sourceFingerprint,
        robotType: robotType.value.trim() || undefined,
      })
  if (session.existingDataset) {
    item.dataset = session.existingDataset
    return
  }
  if (!session.id) throw new Error('上传会话创建失败')
  item.sessionId = session.id
  const completed = new Set(session.uploadedParts)
  for (let part = 0; part < session.totalChunks; part += 1) {
    if (completed.has(part)) {
      updateProgress(item, Math.min((part + 1) * session.chunkSize, item.file.size))
      continue
    }
    if (item.status !== 'uploading') return
    const startByte = part * session.chunkSize
    const blob = item.file.slice(startByte, Math.min(startByte + session.chunkSize, item.file.size))
    await uploadDataPart(
      session.id,
      part,
      blob,
      (loaded) => updateProgress(item, startByte + loaded),
      item.controller!.signal,
    )
  }
  item.dataset = await completeDataUpload(session.id)
}

async function pause(item: UploadItem) {
  if (!item.sessionId) return
  item.status = 'paused'
  item.controller?.abort()
  await pauseDataUpload(item.sessionId)
}

function resume(item: UploadItem) {
  if (item.status !== 'paused' && item.status !== 'error') return
  void start(item)
}

async function cancel(item: UploadItem) {
  item.status = 'cancelled'
  item.controller?.abort()
  if (item.sessionId) await cancelDataUpload(item.sessionId)
}

function retry(item: UploadItem) {
  item.loaded = 0
  item.progress = 0
  void start(item)
}

function statusText(item: UploadItem) {
  return {
    pending: '等待开始',
    uploading: '正在上传',
    paused: '已暂停',
    success: item.dataset?.status === 'PROCESSING' ? '等待预处理' : '上传成功',
    error: '上传失败',
    cancelled: '已取消',
  }[item.status]
}

function formatDuration(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return '—'
  if (seconds < 60) return `${Math.ceil(seconds)} 秒`
  return `${Math.ceil(seconds / 60)} 分钟`
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!activeUploads.value.length) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', onBeforeUnload)
  void loadOptions()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  queue.forEach((item) => item.controller?.abort())
})
</script>

<template>
  <section class="admin-page data-upload">
    <PageHeader
      title="数据上传"
      eyebrow="Data / Upload"
      description="将本地采集数据导入项目，上传完成后统一进入数据管理。"
    />

    <StatePanel v-if="loading" state="loading" />
    <StatePanel
      v-else-if="error"
      state="error"
      title="上传配置加载失败"
      :description="error"
      @retry="loadOptions"
    />
    <template v-else-if="options">
      <section class="upload-context">
        <label>
          <span>所属项目</span>
          <el-select v-model="projectId" placeholder="请先选择项目">
            <el-option
              v-for="project in options.projects"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </label>
        <label>
          <span>云存储</span>
          <el-select v-model="storageKey" placeholder="请选择云存储">
            <el-option
              v-for="storage in options.storages"
              :key="storage.key"
              :label="`${storage.name} · ${storage.bucket}`"
              :value="storage.key"
            />
          </el-select>
        </label>
      </section>

      <section class="access-section">
        <header>
          <div>
            <p>接入方式</p>
            <h2>选择数据格式</h2>
          </div>
          <span>超过 {{ formatBytes(options.multipartThreshold) }} 自动使用分片上传</span>
        </header>
        <div class="mode-grid">
          <button
            v-for="mode in modes"
            :key="mode.type"
            class="mode-option"
            :class="{ 'mode-option--active': dataType === mode.type }"
            type="button"
            @click="dataType = mode.type"
          >
            <el-icon :size="19"><component :is="mode.icon" /></el-icon>
            <strong>{{ mode.label }}</strong>
            <small>{{ mode.hint }}</small>
          </button>
        </div>
        <label v-if="dataType === 'HDF5'" class="robot-field">
          <span>机器人类型</span>
          <el-input v-model="robotType" placeholder="请输入预处理管道配置的机器人类型" />
        </label>
        <el-alert
          v-if="!videoCompatible"
          type="error"
          title="浏览器不兼容"
          description="视频转换需要 MediaStreamTrackProcessor 与 captureStream，请改用 Chrome 94+ 或 Edge 94+。"
          :closable="false"
          show-icon
        />
      </section>

      <input
        ref="fileInput"
        class="visually-hidden"
        type="file"
        multiple
        :accept="selectedMode.accept"
        @change="enqueue(($event.target as HTMLInputElement).files ?? [])"
      >
      <button
        v-permission="'data:upload:create'"
        class="data-dropzone"
        :class="{ 'data-dropzone--dragging': dragging, 'data-dropzone--disabled': !ready || !videoCompatible }"
        type="button"
        @click="chooseFiles"
        @dragenter.prevent="dragging = true"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="dragging = false; enqueue($event.dataTransfer?.files ?? [])"
      >
        <el-icon :size="28"><UploadFilled /></el-icon>
        <strong>拖拽{{ selectedMode.label }}到此处，或点击选择</strong>
        <span v-if="!ready">{{ projectId == null ? '请先选择项目' : '请选择云存储' }}</span>
        <span v-else>支持批量选择；空文件会被拒绝</span>
      </button>

      <section class="queue-section">
        <header>
          <div>
            <p>上传队列</p>
            <h2>{{ queue.length ? `${queue.length} 个文件` : '暂无文件' }}</h2>
          </div>
        </header>
        <StatePanel
          v-if="!queue.length"
          state="empty"
          title="等待选择数据"
          description="选择接入方式并添加文件后，逐文件状态会显示在这里。"
        />
        <ul v-else class="upload-queue">
          <li v-for="item in queue" :key="item.id">
            <div class="queue-file">
              <strong>{{ item.file.name }}</strong>
              <span>{{ formatBytes(item.file.size) }} · {{ statusText(item) }}</span>
              <small v-if="item.error">{{ item.error }}</small>
            </div>
            <div class="queue-progress">
              <el-progress
                :percentage="item.progress"
                :status="item.status === 'success' ? 'success' : item.status === 'error' ? 'exception' : undefined"
              />
              <span v-if="item.status === 'uploading'">
                {{ formatBytes(item.speed) }}/s · 剩余 {{ formatDuration(item.eta) }}
              </span>
            </div>
            <div class="queue-actions">
              <el-tooltip v-if="item.status === 'uploading' && item.sessionId" content="暂停">
                <el-button :icon="VideoPause" circle aria-label="暂停上传" @click="pause(item)" />
              </el-tooltip>
              <el-tooltip v-if="item.status === 'paused'" content="继续">
                <el-button :icon="VideoPlay" circle aria-label="继续上传" @click="resume(item)" />
              </el-tooltip>
              <el-tooltip v-if="item.status === 'error'" content="重试">
                <el-button :icon="Refresh" circle aria-label="重试上传" @click="retry(item)" />
              </el-tooltip>
              <el-tooltip v-if="!['success', 'cancelled'].includes(item.status)" content="取消">
                <el-button :icon="Close" circle aria-label="取消上传" @click="cancel(item)" />
              </el-tooltip>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.data-upload {
  display: grid;
  gap: 26px;
}

.upload-context {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 320px));
  gap: 18px;
}

.upload-context label,
.robot-field {
  display: grid;
  gap: 7px;
}

.upload-context label > span,
.robot-field > span {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.access-section,
.queue-section {
  border-top: 1px solid var(--color-border-strong);
  padding-top: 20px;
}

.access-section > header,
.queue-section > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.access-section header p,
.queue-section header p {
  margin: 0 0 4px;
  color: var(--color-accent);
  font-family: var(--font-mono);
  font-size: 10px;
  text-transform: uppercase;
}

.access-section h2,
.queue-section h2 {
  margin: 0;
  font-size: 17px;
}

.access-section header > span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--color-border);
  border-left: 1px solid var(--color-border);
}

.mode-option {
  display: grid;
  min-height: 94px;
  padding: 15px;
  cursor: pointer;
  border: 0;
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  background: transparent;
  text-align: left;
}

.mode-option strong {
  margin-top: 8px;
  color: var(--color-text-primary);
  font-size: 13px;
}

.mode-option small {
  margin-top: 3px;
  color: var(--color-text-muted);
}

.mode-option:hover,
.mode-option--active {
  color: var(--color-accent);
  background: #eef6f7;
}

.mode-option--active {
  box-shadow: inset 0 2px var(--color-accent);
}

.robot-field {
  width: min(420px, 100%);
  margin-top: 16px;
}

.data-dropzone {
  position: relative;
  display: grid;
  min-height: 150px;
  place-items: center;
  align-content: center;
  gap: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1px dashed #aebdc6;
  border-radius: 6px;
  color: var(--color-text-secondary);
  background: #fbfcfd;
}

.data-dropzone strong {
  color: var(--color-text-primary);
}

.data-dropzone--dragging {
  border-color: var(--color-accent);
  background: #eef6f7;
}

.data-dropzone--disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.upload-queue {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-border);
}

.upload-queue li {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) minmax(220px, 1fr) auto;
  align-items: center;
  gap: 20px;
  min-height: 78px;
  border-bottom: 1px solid var(--color-border);
}

.queue-file,
.queue-progress {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.queue-file strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-file span,
.queue-progress > span {
  color: var(--color-text-muted);
  font-size: 11px;
}

.queue-file small {
  color: var(--color-danger);
}

.queue-actions {
  display: flex;
  gap: 4px;
}

@media (max-width: 900px) {
  .mode-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .upload-queue li {
    grid-template-columns: 1fr auto;
  }

  .queue-progress {
    grid-column: 1 / -1;
    grid-row: 2;
    padding-bottom: 12px;
  }
}

@media (max-width: 620px) {
  .upload-context,
  .mode-grid {
    grid-template-columns: 1fr;
  }

  .access-section > header {
    align-items: start;
    flex-direction: column;
  }
}
</style>
