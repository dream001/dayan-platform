# Dayan Management Platform

Vue 3 + Spring Boot 3 前后端分离管理平台，使用 PostgreSQL 持久化业务数据，MinIO 存储文件对象。

## 启动

```bash
cp .env.example .env
```

启动前必须修改 `.env` 中的 `JWT_SECRET`、`INITIAL_ADMIN_PASSWORD`、`POSTGRES_PASSWORD` 和 `MINIO_ROOT_PASSWORD`。

```bash
docker compose up -d --build
docker compose ps
```

当前运行环境：

- 管理端：http://localhost:8890
- 后端健康检查：http://localhost:5004/actuator/health
- MinIO Console：http://localhost:9011
- PostgreSQL：localhost:5435

当前登录账户：

- 账号：`e2e_admin`
- 密码：`AdminE2E_2026_Strong!`

使用 `.env.example` 重新初始化环境时，默认账号为 `admin`，默认密码为
`change_me_now_2026`；也可以通过 `INITIAL_ADMIN_USERNAME` 和
`INITIAL_ADMIN_PASSWORD` 修改。

首次登录后请立即修改密码。

若端口已被占用，可在 `.env` 中覆盖 `FRONTEND_PORT`、`BACKEND_PORT`、`POSTGRES_PORT`、`MINIO_API_PORT` 和 `MINIO_CONSOLE_PORT`。

首次启动时，Flyway 自动创建数据库结构和基础权限；启用 `INITIAL_ADMIN_ENABLED` 后，后端使用配置中的管理员信息创建初始账号，密码仅以 BCrypt 哈希保存。

## 本地验证

```bash
cd backend
./mvnw clean test package
```

```bash
cd frontend
npm ci
npm test
npm run type-check
npm run lint
npm run build
```
