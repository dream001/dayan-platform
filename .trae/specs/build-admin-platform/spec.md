# 搭建后端管理平台 Spec

## Why
当前工作区为空，需要从零搭建一套前后端分离、可直接运行和继续扩展的通用管理平台。首期以标准 RBAC 权限底座、组织与文件管理为核心，并确保所有业务数据来自 PostgreSQL 和 MinIO，不使用 Mock 数据。

## What Changes
- 建立单仓库工程，前端与后端分别位于 `frontend/`、`backend/`。
- 前端采用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios 和 Element Plus。
- 后端采用 Java 21、Spring Boot 3、Spring Security、JWT 和 MyBatis-Plus。
- 后端严格划分 Controller、Service、Repository/Mapper、Model，并使用 DTO/VO 隔离接口与持久化模型。
- 建立用户、角色、菜单、按钮权限、部门、认证会话、文件元数据和操作日志的数据模型。
- 使用 PostgreSQL 持久化业务数据，并通过版本化数据库迁移脚本初始化结构和管理员账号。
- 使用 MinIO 存储文件对象，PostgreSQL 仅保存对象键、原始文件名、大小、类型、上传人等元数据。
- 提供登录、个人中心、工作台、用户管理、角色管理、菜单权限、部门管理、文件管理和操作日志界面。
- 提供统一响应、参数校验、异常处理、分页、鉴权、权限指令和审计日志。
- 提供前后端 Dockerfile、Docker Compose、健康检查和环境变量模板，实现 PostgreSQL、MinIO、后端、前端一键启动。
- 提供后端单元/集成测试和前端关键流程测试，覆盖认证、权限与文件管理。

## Impact
- Affected specs: 工程基础、身份认证、RBAC 权限、组织管理、文件管理、操作审计、部署运行
- Affected code: `frontend/`、`backend/`、`docker-compose.yml`、环境配置与数据库迁移脚本

## ADDED Requirements

### Requirement: 工程与技术基线
系统 SHALL 采用前后端分离的单仓库结构，前端通过 REST API 调用后端，且代码能够独立构建和测试。

#### Scenario: 独立构建
- **WHEN** 开发者分别执行前端和后端构建命令
- **THEN** 两个项目均可独立完成依赖安装、编译和测试

#### Scenario: 分层边界
- **WHEN** 新增或修改后端业务能力
- **THEN** HTTP 处理、业务规则、数据访问和数据模型分别位于 Controller、Service、Repository/Mapper、Model 层

### Requirement: 身份认证
系统 SHALL 提供基于 Spring Security 与 JWT 的登录认证，密码使用安全哈希存储，刷新令牌仅以哈希形式持久化。

#### Scenario: 登录成功
- **WHEN** 启用用户提交正确用户名和密码
- **THEN** 系统返回访问令牌、刷新令牌及当前用户基础信息和权限集合

#### Scenario: 登录失败
- **WHEN** 用户名或密码错误、账号被禁用或账号不存在
- **THEN** 系统拒绝登录并返回不泄露账号存在性的统一错误

#### Scenario: 刷新与退出
- **WHEN** 用户刷新令牌或主动退出
- **THEN** 系统按会话状态签发新令牌或使对应刷新会话失效

### Requirement: 用户与部门管理
系统 SHALL 支持管理员分页查询、新增、编辑、启停、重置密码和分配用户角色，并维护树形部门结构。

#### Scenario: 管理用户
- **WHEN** 有权限的管理员提交合法用户信息
- **THEN** 系统持久化用户、所属部门和角色关系，并在列表中返回真实数据库结果

#### Scenario: 禁用用户
- **WHEN** 管理员禁用一个用户
- **THEN** 该用户后续无法登录，已有刷新会话失效

#### Scenario: 管理部门
- **WHEN** 管理员新增、编辑或删除部门节点
- **THEN** 系统维护合法的父子关系，并阻止删除仍有子部门或用户的部门

### Requirement: RBAC 权限管理
系统 SHALL 通过用户、角色、菜单和按钮权限控制后端接口及前端可见操作，前端隐藏不代表后端放弃鉴权。

#### Scenario: 角色授权
- **WHEN** 管理员为角色勾选菜单和按钮权限
- **THEN** 系统保存角色权限关系，并在该角色用户重新认证后生效

#### Scenario: 接口拒绝
- **WHEN** 已登录用户调用无权访问的接口
- **THEN** 后端返回 403，且不执行目标业务操作

#### Scenario: 动态导航
- **WHEN** 用户进入管理端
- **THEN** 前端根据后端返回的菜单树生成可访问导航，并依据权限码控制按钮

### Requirement: 文件管理
系统 SHALL 通过后端接收文件并存入 MinIO，通过 PostgreSQL 保存文件元数据，支持分页查询、上传、下载、预览链接和删除。

#### Scenario: 上传文件
- **WHEN** 有权限用户上传符合大小和类型限制的文件
- **THEN** 文件对象写入 MinIO，元数据写入 PostgreSQL，并返回稳定的文件标识

#### Scenario: 上传回滚
- **WHEN** 对象写入或元数据写入任一环节失败
- **THEN** 系统清理已产生的不完整数据，不返回成功结果

#### Scenario: 下载或预览
- **WHEN** 有权限用户请求有效文件
- **THEN** 系统返回受时效和权限约束的下载响应或预签名地址

#### Scenario: 删除文件
- **WHEN** 有权限用户删除文件
- **THEN** 系统删除 MinIO 对象和数据库元数据，并记录操作结果

### Requirement: 操作日志
系统 SHALL 记录登录、用户、角色、菜单、部门和文件管理等关键写操作，日志包含操作者、操作类型、目标、结果、时间、IP 和请求标识，且不记录密码或令牌。

#### Scenario: 查询审计记录
- **WHEN** 有审计权限的管理员按用户、模块、结果或时间范围筛选
- **THEN** 系统从 PostgreSQL 分页返回匹配的操作日志

#### Scenario: 业务操作失败
- **WHEN** 被审计的业务操作抛出异常
- **THEN** 日志记录失败状态和经过脱敏的错误摘要

### Requirement: 管理端体验
系统 SHALL 提供安静、紧凑、适合高频操作的响应式管理界面，以导航、筛选、表格、抽屉和对话框组织工作流，避免营销式首屏和装饰性卡片堆叠。

#### Scenario: 桌面端操作
- **WHEN** 用户在常见桌面分辨率访问系统
- **THEN** 侧边导航、顶部上下文区和主工作区清晰可扫描，列表筛选与主要操作无需横向滚动

#### Scenario: 窄屏访问
- **WHEN** 用户在移动端或窄屏访问系统
- **THEN** 导航可收起，表格提供可用的横向滚动或信息折叠，文本和操作控件不重叠

#### Scenario: 请求状态
- **WHEN** 页面加载、提交成功、提交失败或数据为空
- **THEN** 界面提供明确且一致的加载、反馈、错误和空状态

### Requirement: 工作台与个人中心
系统 SHALL 提供来源于真实数据的工作台摘要，以及当前用户资料和密码修改能力。

#### Scenario: 查看工作台
- **WHEN** 已登录用户进入首页
- **THEN** 系统展示其可访问范围内的用户、文件和近期操作等真实统计，不展示 Mock 数据

#### Scenario: 修改密码
- **WHEN** 用户提交正确旧密码和符合策略的新密码
- **THEN** 系统更新密码、撤销其他刷新会话，并要求重新认证

### Requirement: API 一致性与安全
系统 SHALL 提供统一响应格式、分页协议、校验错误、错误码和请求追踪标识，并对跨域、上传限制、敏感配置和常见 Web 风险进行约束。

#### Scenario: 参数非法
- **WHEN** 客户端提交不符合约束的字段
- **THEN** 系统返回 400、稳定错误码和可定位字段的提示

#### Scenario: 未认证请求
- **WHEN** 未携带有效访问令牌的请求访问受保护资源
- **THEN** 系统返回 401，不暴露内部堆栈或数据库信息

### Requirement: 数据库迁移与初始化
系统 SHALL 使用版本化迁移管理 PostgreSQL 表结构、索引、约束和基础权限数据。

#### Scenario: 空数据库首次启动
- **WHEN** 后端连接到空 PostgreSQL 数据库
- **THEN** 迁移自动创建所需结构、基础菜单权限和可配置的初始管理员

#### Scenario: 重复启动
- **WHEN** 同一版本应用重复启动
- **THEN** 已执行迁移不会重复破坏或覆盖现有业务数据

### Requirement: 容器化运行
系统 SHALL 提供可配置的 Docker Compose 编排 PostgreSQL、MinIO、后端和前端，并为持久化服务配置数据卷和健康检查。

#### Scenario: 一键启动
- **WHEN** 开发者配置环境变量并执行 Docker Compose 启动命令
- **THEN** 所有服务按依赖顺序进入健康状态，管理端可访问且能连接真实 PostgreSQL 与 MinIO

#### Scenario: 重启保留数据
- **WHEN** 容器停止后重新启动
- **THEN** PostgreSQL 数据与 MinIO 对象通过数据卷保留

### Requirement: 质量验证
系统 SHALL 对认证、RBAC、用户管理和文件上传下载提供自动化测试，并通过静态检查和构建校验。

#### Scenario: 执行持续验证
- **WHEN** 执行项目约定的测试与构建命令
- **THEN** 后端测试、前端测试、类型检查和生产构建全部通过

## MODIFIED Requirements

无，本项目为新建工程。

## REMOVED Requirements

无。
