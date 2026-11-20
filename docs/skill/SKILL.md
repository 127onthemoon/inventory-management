---
name: inventory-concurrency-dev
description: 用于开发、修改、测试「高并发库存管理系统」（Java 21 + Spring Boot 3 + JPA + MySQL + Thymeleaf）。凡涉及库存变更、出入库、幂等、事务、并发测试、压测或页面开发时先读本 Skill。
---

# 高并发库存管理系统 · 项目开发 Skill

> 用途：作为 AI 编程助手（或新加入的开发者）的“项目规约”。它把需求、约束与验收标准写成可执行的规则，
> AI 在生成或修改代码前必须遵守，完成后必须按「验收清单」自检。

## 1. 项目定位

* 本科《Java语言及网络编程》结课项目，单体架构，**可解释、可复现**优先于炫技。
* 核心亮点：数据库层面的并发库存控制（防超卖、防负库存、防重复扣减、库存与流水强一致）。

## 2. 技术约束（必须遵守）

| 允许 | 禁止（除非有无法用现有技术解决的问题，并写入决策记录） |
|---|---|
| Java 21、Spring Boot 3.x、Spring MVC、Spring Data JPA/Hibernate、Spring Security | Redis、消息队列、微服务、分布式锁 |
| MySQL 8（InnoDB），Maven，JUnit 5，Mockito，MockMvc | Vue / React / 任何前端构建工具 |
| Thymeleaf + 原生 HTML/CSS/JS（单个 app.css、app.js） | 为展示而引入的无用依赖（Lombok、Swagger 等不引入） |

## 3. 分层与目录规范

```
controller/web   页面控制器：只做参数接收、调用服务、选择视图；捕获 BusinessException 回显表单
controller/api   REST 控制器：返回 ApiResponse；异常由 ApiExceptionHandler 统一映射状态码
service          业务逻辑；库存变更只能出现在 StockTxService
repository       Spring Data JPA；库存相关 SQL 只能写在 ProductRepository
entity / dto     实体与表单/请求/结果对象（DTO 优先使用 record）
db/schema.sql    表结构唯一来源（Hibernate ddl-auto=validate）
```

* 新增表/字段：先改 `schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行），再改实体；启动校验必须通过。
* 所有代码注释、页面文案使用中文；并发控制相关代码必须写清“为什么这样做”。

## 4. 库存并发控制规则（最重要）

1. **唯一入口**：库存只能经 `StockService.stockIn / stockOut` 改变。`Product.stock` 保持 `insertable=false, updatable=false`，
   任何代码都不得通过 `product.setStock(...)`+`save()` 修改库存（实体上也不提供 setStock）。
2. **原子条件更新**：
   * 出库：`UPDATE product SET stock = stock - :qty WHERE id = :id AND status = 1 AND stock >= :qty`，影响 0 行 ⇒ 库存不足。
   * 入库：`UPDATE product SET stock = stock + :qty WHERE id = :id AND status = 1`。
   * **禁止**：`SELECT stock` 后在 Java 中计算再 `UPDATE ... SET stock = ?`；**禁止**以 `synchronized`/`ReentrantLock` 作为并发方案。
3. **事务顺序**（`StockTxService`，`@Transactional(rollbackFor = Exception.class)`）：
   ① 原子更新 → ② `SELECT stock ... FOR UPDATE` 读变动后库存 → ③ `saveAndFlush` 单据 → ④ `saveAndFlush` 流水。
   任一步异常必须抛出运行时异常，使整个事务回滚。
4. **幂等**：每张单据必须带 `request_id`（唯一约束）。页面表单打开时生成 UUID 隐藏域；接口支持请求体 `requestId` 或请求头 `Idempotency-Key`。
   门面服务先查 request_id 快速返回；唯一键冲突后查询已存在单据并返回 `duplicate=true`。
5. **重试**：只对 `PessimisticLockingFailureException`（死锁/锁等待超时）和单据号碰撞重试，最多 3 次，且必须在事务之外重试。
6. **数据库兜底**：`CHECK (stock >= 0)`、`request_id`/`order_no`/`ref_order_no` 唯一约束不得删除。
7. **期初库存**：初始化数据中的库存必须同时写“期初入库单 + 入库流水”，保证一致性不变式从第一天成立。

## 5. 业务规则

* 数量为 1 ~ 1,000,000 的整数；库存不足拒绝出库，不产生单据/流水。
* 停用商品不能出入库；有流水的商品不能删除；有商品的分类、被引用的供应商不能删除。
* 角色：ADMIN 全部权限；OPERATOR 只能查询与办理出入库，不能维护基础资料和用户（URL 级 + `@PreAuthorize` 双重控制）。
* REST 状态码：成功/重复 200，库存不足 409，参数错误 400，未登录 401，无权限 403，不存在 404，并发冲突 503，系统错误 500。

## 6. 测试规范

* 测试必须连接真实 MySQL 测试库 `inventory_test`（`application-test.yml`），**不得用 H2 代替**并发测试。
* 每个测试自建商品（`AbstractMySqlIntegrationTest.createProduct(initialStock)`），互不依赖、无需清库。
* 并发测试模板：固定线程池 + `CountDownLatch` 同时放行 + 原子计数器分类统计（成功 / 库存不足 / 重复 / 其他错误）。
* 每个并发测试结束都要调用一致性断言：库存 = 流水净变动、流水条数 = 单据数、流水链连续、无负数。
* 新增库存相关功能时，至少补充：正常、库存不足、重复提交、异常回滚、并发 5 类用例。

## 7. 验收清单（完成任何改动后逐条自检）

- [ ] `mvn clean package` 成功，`mvn test` 0 失败 0 错误
- [ ] `ConcurrentStockTest` 全部通过：100/500 并发抢 50 成功恰为 50、库存 0；同幂等号只扣一次；混合场景一致
- [ ] 应用以 MySQL 启动成功，`scripts/check-pages.sh` 全部通过，`scripts/smoke-ui.sh` 全部通过
- [ ] `perf/run-perf.sh` 压测系统错误率 0%，`*-db-check.txt` 全部 OK
- [ ] 文档中的所有数字都来自 `docs/test-results/` 与 `perf/results/` 中的原始输出，**不得编造**
- [ ] 个人信息一律使用「【待填写】」占位

## 8. 常用命令

```bash
source ~/tools/env.sh                 # 本机 JDK/Maven/JMeter/MySQL 环境变量
mvn clean package                     # 编译 + 全部测试 + 打包
scripts/start.sh 8080 / scripts/stop.sh
scripts/check-pages.sh && scripts/smoke-ui.sh
perf/run-perf.sh                      # 全部压测场景；也可指定：perf/run-perf.sh burst100 burst500
mysql ... -e "SET @pid=NULL; SOURCE scripts/check-consistency.sql;"
```
