# 合同回款管理系统

公司内部使用的合同与回款管理系统。

### 文档导航

| 类别 | 文档 |
| --- | --- |
| 产品 / 需求 | [需求说明书](./需求说明书.md) |
| 架构 / 技术 | [技术规格说明书](./技术规格说明书.md) |
| 项目管理 | [任务拆解 WBS](./任务拆解WBS.md) · [代码生成计划](./代码生成计划.md) · [后续迭代计划](./后续迭代计划.md) |
| **运维 / 上线** | **[部署文档](./部署文档.md)** · **[ops/ 单机部署脚本](./ops/README.md)** |
| **业务 / 培训** | **[系统操作手册](./系统操作手册.md)** |

---

## 技术栈

- 前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia
- 后端：Spring Boot 3 + Java 17 + MyBatis-Plus + Spring Security + JWT
- 存储：MySQL 8 + Redis 7 + MinIO

---

## 快速启动（开发环境）

### 1. 准备环境变量

```bash
cp .env.example .env
```

### 2. 拉起依赖（MySQL / Redis / MinIO）

```bash
docker compose -f docker-compose.dev.yml --env-file .env up -d
```

启动后：

- MySQL：`localhost:3306`（账号 root / 默认密码 `root123456`，库名 `contract_mgmt`）
- Redis：`localhost:6379`（默认密码 `redis123456`）
- MinIO：API `http://localhost:9000`、控制台 `http://localhost:9001`（默认 `minioadmin` / `minioadmin123`）

> 首次启动后请到 MinIO 控制台创建 bucket：`contract-mgmt`（或由应用启动时自动创建）。

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

启动后访问：

- API：`http://localhost:8080/api/v1/...`
- Swagger：`http://localhost:8080/swagger-ui.html`

> Flyway 会自动建表并写入初始数据，默认管理员账号：
> - 用户名：`admin`
> - 密码：`Admin@1234`
> - 首次登录需修改密码

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

启动后访问：`http://localhost:5173`

---

## 项目结构

```
contract-mgmt/
├── backend/                       # Spring Boot 后端
├── frontend/                      # Vue 3 前端
├── ops/                           # 单机部署脚本（云主机一键安装/升级/备份/恢复/卸载）
├── docker-compose.dev.yml         # 开发环境基础设施
├── .env.example                   # 环境变量模板
└── 需求说明书.md, 技术规格说明书.md, 任务拆解WBS.md, 代码生成计划.md, 部署文档.md, 系统操作手册.md, 后续迭代计划.md
```

---

## 演示路径（端到端业务闭环）

1. 浏览器打开 `http://localhost:5173`
2. 使用 `admin / Admin@1234` 登录（首次会被引导改密）
3. 进入"合同管理 → 新建合同"，录入一份合同
4. 保存后跳转"生成回款计划"页，选择分期数（如 3 期）自动生成
5. 进入"回款管理 → 登记实际回款"，选合同与期次，录入到账金额
6. 回到首页驾驶舱可看到统计数据已变化
7. 进入合同详情，查看"回款计划"Tab 的核销进度

---

## 当前已实现 vs 待补

详见 [代码生成计划.md](./代码生成计划.md) §1.2 与 §6。

### 已实现（可立即使用）

**后端**

- 基础设施：MySQL / Redis / MinIO Docker Compose 一键启动
- 数据库：Flyway 自动建表（9 张表 + 初始管理员 + 系统参数）
- 鉴权：JWT 登录、登出（token 黑名单）、修改密码、登录失败 5 次锁定 10 分钟
- 安全：BCrypt 密码哈希、RBAC（销售员/管理员）、数据级越权校验
- 审计：`@Audit` AOP 注解，所有关键写操作异步入 `audit_log`
- 合同：编号自动生成（Redis INCR + 启动校对）、CRUD、查询、状态机、首页驾驶舱
- 回款：回款计划生成（手工 / 等额均分 / 按比例）、实际回款登记/删除、**核销算法（SELECT FOR UPDATE 锁合同 + 期次自动抵扣 + 状态机联动）**
- 用户管理、系统参数 CRUD（含 Redis 缓存）
- 全局异常处理 + 统一响应格式 + Swagger UI

**前端**

- Vue 3 + TypeScript + Element Plus 完整工程骨架
- 登录页、强制改密页
- 首页驾驶舱（合同总数/金额/已收/应收/状态分布）
- 合同模块：列表（多条件筛选 + 分页）、新建/编辑表单、详情（回款计划进度条 + 实际回款流水）、作废
- 回款模块：计划生成器（三种模式）、登记回款（搜索合同 + 选期次或自动核销）、回款列表
- Axios 统一拦截、Pinia 状态管理、路由守卫（含强制改密拦截）

### 待补

完整清单、排期、人天估算与验收标准已沉淀到 [后续迭代计划.md](./后续迭代计划.md)，按 4 组共 22 个迭代项（≈ 53 人天）推进：

| 组别 | 关键内容 | 估算 |
| --- | --- | --- |
| IT-1 上线必备 | 附件链路、报表、定时任务、操作日志/系统参数/用户管理页、限流硬化 | ≈ 20 人天 |
| IT-2 工程实践 | 核销算法单测（P0）、接口测试、性能/安全/兼容性回归 | ≈ 13.5 人天 |
| IT-3 体验增强 | 全量异步导出、站内消息中心、个人中心、错误兜底、通用组件 | ≈ 10.5 人天 |
| IT-4 上线运维 | 生产 Dockerfile、Nginx + HTTPS、CI/CD、备份脚本、监控告警、用户手册 | ≈ 9 人天 |

> 建议 3 个 Sprint × 2 周完成迭代到上线就绪。

---

## 故障排查

| 现象 | 排查方向 |
| --- | --- |
| 后端启动报 `Public Key Retrieval is not allowed` | 已配置 `allowPublicKeyRetrieval=true`，检查是否使用了示例 yml |
| 后端启动报 `Access denied for user 'root'@'xxx'` | 宿主机 3306 端口被其它项目的 MySQL 容器占用了，Spring Boot 连到了别人的库。先 `docker ps` 看一下是否有同名/同端口的容器，停掉冲突项后 `docker compose -f docker-compose.dev.yml --env-file .env down && up -d` |
| 登录提示"用户名或密码错误"但密码正确 | 检查 `sys_user.password_hash` 是否为 `$2b$10$...` 开头，并清除 Redis `login:lock:*` 与 `login:fail:*` |
| Flyway 报 checksum mismatch | 开发期间修改了已应用脚本：删表后重启，或新增 `V2__xxx.sql` 增量脚本 |
| 前端报跨域 | `vite.config.ts` 已配置代理 `/api → http://localhost:8080`；确保后端启动 |
| MinIO 找不到 bucket | 应用启动时会自动创建，若失败请到 `http://localhost:9001` 手动创建 `contract-mgmt` |
| 看到 `couldn't find env file: .env` | 项目里只有 `.env.example` 模板，需先 `cp .env.example .env` |
| 看到 `./mvnw: no such file or directory` | Maven Wrapper 丢了，在 `backend/` 下跑 `mvn -N wrapper:wrapper -Dmaven=3.9.6` 重新生成 |
| 启动报 `Web server failed to start. Port 8080 was already in use` | 用 `lsof -nP -iTCP:8080 -sTCP:LISTEN` 看占用进程，通常是上一次没关的 Spring Boot 或其它服务；`kill <PID>` 即可 |

---

## 许可

公司内部使用。
