# 大雁管理平台（Dayan Platform）

前后端分离的通用后台管理平台，提供标准 RBAC 权限底座、组织管理、文件管理与操作审计。所有业务数据均来自 PostgreSQL 和 MinIO，不使用 Mock 数据，支持通过 Docker Compose 一键启动。

## 功能特性

- 身份认证：基于 Spring Security + JWT 的登录、令牌刷新与退出，刷新令牌仅以哈希形式持久化
- RBAC 权限：用户、角色、菜单与按钮权限，菜单权限同时驱动后端鉴权与前端导航
- 组织管理：树形部门结构维护，用户归属部门与角色分配
- 用户管理：用户分页查询、新增、编辑、启停、重置密码与角色分配
- 角色管理：角色维护、角色用户与菜单/按钮权限树授权
- 菜单管理：菜单树维护，支持菜单与按钮两类权限节点
- 文件管理：文件上传、分页查询、预览、下载与删除，对象存于 MinIO，元数据存于 PostgreSQL
- 操作审计：关键操作自动记录审计日志，支持分页筛选与详情查看
- 工作台：真实数据驱动的用户、文件与近期操作统计
- 工程基线：统一响应封装、请求标识、全局异常处理、参数校验、健康检查与环境变量模板

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Element Plus |
| 后端 | Java 21、Spring Boot 3.5、Spring Security、JWT、MyBatis-Plus、Flyway |
| 数据库 | PostgreSQL 16 |
| 对象存储 | MinIO |
| 部署 | Docker、Docker Compose |
| 测试 | JUnit、Testcontainers、Vitest、Vue Test Utils |

## 目录结构

```text
dayan-platform/
├── backend/              # Java 21 Spring Boot 后端
│   └── src/main/java/com/dayan/platform/
│       ├── controller/   # HTTP 接口层
│       ├── service/      # 业务逻辑层
│       ├── mapper/       # MyBatis 数据访问层
│       ├── model/        # 持久化模型
│       ├── dto/ vo/      # 入参与出参对象
│       ├── config/       # 安全、存储等配置
│       ├── security/     # 认证主体与用户服务
│       ├── audit/        # 审计切面
│       └── common/       # 统一响应、异常与链路追踪
├── frontend/             # Vue 3 TypeScript 前端
│   └── src/
│       ├── views/        # 页面
│       ├── components/   # 通用组件
│       ├── layouts/      # 应用外壳
│       ├── router/       # 路由与动态菜单
│       ├── stores/       # Pinia 状态
│       ├── services/     # API 调用
│       ├── directives/   # 权限指令
│       └── types/        # 类型定义
├── docker-compose.yml    # 本地完整服务拓扑
└── .env.example          # 环境变量模板
```

## 快速开始（Docker Compose）

前置要求：已安装 Docker 与 Docker Compose。

1. 准备环境变量：

```bash
cp .env.example .env
```

2. 修改 `.env` 中以下必填项（默认值仅供本地演示）：

- `JWT_SECRET`：至少 32 个随机字符
- `INITIAL_ADMIN_PASSWORD`：初始管理员密码
- `POSTGRES_PASSWORD`、`MINIO_ROOT_PASSWORD`：数据库与 MinIO 密码

3. 构建并启动全部服务：

```bash
docker compose up -d --build
```

4. 访问地址：

| 服务 | 地址 |
| --- | --- | --- |
| 前端 | http://localhost:8887 |
| 后端 API | http://localhost:5001/api/v1 |
| MinIO Console | http://localhost:9001 |
| PostgreSQL | localhost:5434 |

5. 使用 `.env` 中配置的初始管理员账号登录（默认用户名 `admin`）。首次登录后请立即修改密码。

停止服务：

```bash
docker compose down
```

如需同时清除数据卷：

```bash
docker compose down -v
```

## 本地开发

### 后端

需要本地可访问的 PostgreSQL 与 MinIO（可仅启动这两个服务）：

```bash
docker compose up -d postgres minio minio-init
```

在 `backend/` 目录运行：

```bash
./mvnw spring-boot:run
```

后端默认监听 `8080`，数据库结构由 Flyway 在启动时自动迁移。关键配置通过环境变量覆盖，如 `DATABASE_URL`、`JWT_SECRET`、`MINIO_ENDPOINT` 等，完整列表见 [.env.example](./.env.example)。

### 前端

在 `frontend/` 目录运行：

```bash
npm install
npm run dev
```

前端开发服务器默认通过代理访问 `http://localhost:5001` 的后端 API，可在 `vite.config.ts` 与环境变量中调整。

## API 概览

所有接口以 `/api/v1` 为前缀，除登录等公开接口外均需携带 JWT。主要分组：

| 分组 | 路径 |
| --- | --- |
| 认证 | `/auth/login`、`/auth/refresh`、`/auth/logout`、`/auth/me` |
| 当前用户菜单 | `/auth/me/menus` |
| 工作台 | `/dashboard/statistics` |
| 用户管理 | `/system/users` |
| 角色管理 | `/system/roles` |
| 菜单管理 | `/system/menus/tree` |
| 部门管理 | `/system/departments/tree` |
| 文件管理 | `/files` |
| 审计日志 | `/audit/logs` |
| 系统信息 | `/system/info` |

## 数据模型

数据库结构由 Flyway 版本化迁移脚本统一管理，脚本位于 `backend/src/main/resources/db/migration/`，从空数据卷启动时会自动建表并初始化 RBAC 基线数据。

核心表：`sys_department`、`sys_user`、`sys_role`、`sys_menu_permission`、`sys_user_role`、`sys_role_permission`、`auth_session`、`file_metadata`、`operation_log`。

文件对象本体保存在 MinIO 的 `dayan-files` 存储桶中，PostgreSQL 仅保存对象键、原始文件名、大小、内容类型与上传人等元数据。

## 测试

后端测试基于 Testcontainers，会启动真实的 PostgreSQL 与 MinIO 容器，运行时需要 Docker：

```bash
cd backend
./mvnw test
```

前端：

```bash
cd frontend
npm test        # 单元测试
npm run lint    # 代码检查
npm run build   # 类型检查与生产构建
```

## 安全说明

- 部署前必须修改 `JWT_SECRET`、初始管理员密码、数据库与 MinIO 密码
- 生产环境应将 `INITIAL_ADMIN_ENABLED` 在初始化完成后关闭
- 接口返回统一的错误信息，登录失败不泄露账号是否存在
- 请勿将包含真实密钥的 `.env` 文件提交到版本库
