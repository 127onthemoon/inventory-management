# 高并发库存管理系统（Java 课程结课项目）

基于 **Java 21 + Spring Boot 3.5 + Spring MVC + Spring Data JPA/Hibernate + MySQL 8 + Thymeleaf** 的单体 Web 项目，
实现商品、分类、供应商、入库、出库、库存流水、数据统计与用户权限管理，
重点展示**数据库原子条件更新 + 事务 + 唯一约束**实现的高并发库存控制：不超卖、不出现负库存、不重复扣减、库存与流水始终一致。

> 不投入生产，仅用于课程展示。测试摘要见 `docs/test-results/` 与 `docs/07-性能测试报告.md`。原始 JMeter 结果与演示视频片段未纳入本仓库。

## 1. 环境要求

| 软件 | 本项目实际使用版本 |
|---|---|
| JDK | Eclipse Temurin 21.0.13 |
| Maven | 3.9.16 |
| MySQL | 8.4.13（8.0.16+ 即可，需要 CHECK 约束支持） |
| JMeter（仅压测） | 5.6.3 |

本机环境变量已写在 `~/tools/env.sh`，使用前执行 `source ~/tools/env.sh`。

## 2. 快速开始

```bash
# 1) 初始化数据库与账号（库：inventory_db 运行 / inventory_test 测试）
#    必须通过环境变量提供 MySQL root 口令与应用账号口令，不要把真实口令写入仓库
MYSQL_ROOT_PASSWORD=<root密码> DB_USER=inventory DB_PASSWORD=<应用账号密码> scripts/init-db.sh
#    表结构和初始数据无需手工导入：应用启动时自动执行 src/main/resources/db/schema.sql 与 data.sql

# 2) 编译 + 运行全部测试 + 打包
mvn clean package

# 3) 后台启动（nohup，日志 app.log，PID 写入 app.pid；端口被占用时传入其它端口，如 scripts/start.sh 8081）
scripts/start.sh
#    等价于：PORT=8080 nohup java -Xms256m -Xmx768m -jar target/inventory-management-1.0.0.jar > app.log 2>&1 &

# 4) 停止
scripts/stop.sh
```

请先设置 `DB_USER`、`DB_PASSWORD`（以及可选的 `DB_HOST`、`DB_PORT`、`DB_NAME`、`PORT`）。配置文件中的默认口令只是占位符 `change_me`，启动前必须改为你自己的口令。

### 演示数据（可选，推荐答辩前执行）

```bash
scripts/seed-demo.sh        # 应用运行中执行：备份 inventory_db → 重建 → 通过表单/接口生成演示数据 → 一致性核对
```

结果是 20 个商品（5 个低库存）、6 个分类、5 个供应商，以及分布在近 14 天的 238 张出入库单。全部通过管理后台表单与 REST 接口产生，只回填了日期，最后由 `check-consistency.sql` 核对全部一致。
只作用于运行库 `inventory_db`，不碰测试库；跑完 `smoke-ui.sh` 或录完视频后可再执行一次恢复干净数据。说明见 `docs/12-演示数据说明.md`。

## 3. 访问地址与默认账号

* 管理后台：<http://127.0.0.1:8080/>（未登录会跳转 `/login`）
* 管理员：`admin / admin123`（全部权限）
* 操作员：`operator / operator123`（查询 + 出入库；不能维护基础资料和用户）
* 以上账号仅供课程演示，若用于任何真实部署，请立即修改默认密码。

## 4. 功能清单

| 模块 | 页面 | 说明 |
|---|---|---|
| 数据统计 | `/` | 商品总数、库存总量、低库存预警、今日/近 7 日出入库、最近流水 |
| 商品管理 | `/products` | 关键字/分类筛选、分页、详情（含一致性核对）、增删改（管理员） |
| 分类管理 | `/categories` | 增删改查，有商品的分类不能删除 |
| 供应商管理 | `/suppliers` | 增删改查，被引用不能删除 |
| 入库管理 | `/stock/in` | 新建入库单（自动带幂等号），入库单列表 |
| 出库管理 | `/stock/out` | 新建出库单（库存不足拒绝，重复提交只扣一次），出库单列表 |
| 库存流水 | `/flows` | 按商品、类型筛选；记录变动前后库存与关联单据 |
| 一致性核对 | `/consistency` | 全部商品：库存 = Σ入库 − Σ出库、流水链连续 |
| 用户管理 | `/users` | 仅管理员：新增、编辑、重置密码、启用/禁用 |

## 5. REST 接口（需登录：会话 Cookie 或 HTTP Basic）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/products` | 商品列表 |
| GET | `/api/products/{id}` | 商品详情与实时库存 |
| POST | `/api/stock/out` | 出库 `{"productId":8,"quantity":1,"requestId":"可选幂等号"}`，也可用请求头 `Idempotency-Key` |
| POST | `/api/stock/in` | 入库 `{"productId":8,"quantity":10,"supplierId":1,"unitPrice":60,"requestId":"..."}` |
| GET | `/api/stock/consistency[?productId=]` | 一致性核对 |

响应约定：200 `OK` 成功 / 200 `DUPLICATE` 重复提交（未重复执行）/ **409 `INSUFFICIENT_STOCK` 库存不足** / 400 参数错误 / 401 未登录 / 403 无权限 / 404 不存在 / 503 并发冲突重试失败 / 500 系统错误。

```bash
curl -u operator:operator123 -H 'Content-Type: application/json' \
     -d '{"productId":8,"quantity":1,"requestId":"demo-001"}' http://127.0.0.1:8080/api/stock/out
# 再执行一次同样的命令 → code=DUPLICATE，库存不会再减
```

## 6. 如何演示高并发防超卖

1. **自动化并发测试**（真实 MySQL）：`mvn test -Dtest=ConcurrentStockTest`，输出中的 `[CONCURRENCY]` 行给出成功数、最终库存与流水核对结果；
   `mvn test -Dtest=NaiveDeductionExperimentTest` 演示错误写法（读-改-写）的超卖现象作对比。
2. **JMeter 压测**：`perf/run-perf.sh burst100 burst500 idempotent`（或不带参数跑全部场景），结果在 `perf/results/`，
   `*-db-check.txt` 为压测后的数据库核对，`*-report/index.html` 为 JMeter HTML 报告。
3. **页面演示**：登录后打开“秒杀演示商品 P9001”（id=8，初始库存 50）→ 执行
   `jmeter -n -t perf/stock-out-burst.jmx -JproductId=8 -Jthreads=100 -Jrampup=2 -l /tmp/demo.jtl` → 刷新商品详情和 `/consistency` 页面，可见库存 0、出库单 50 张、核对一致；
   在出库表单中输入超过库存的数量观察“库存不足”提示；提交成功后浏览器后退再次提交，观察“重复提交”提示。

## 7. 并发设计思路（摘要）

* **原子条件更新**：`UPDATE product SET stock = stock - ? WHERE id = ? AND status = 1 AND stock >= ?`，影响 0 行即库存不足；InnoDB 行锁把并发扣减串行化。
* **同一事务**：扣减 → `SELECT stock ... FOR UPDATE` 读变动后库存 → 写单据 → 写流水；任一步失败整体回滚。
* **幂等**：表单 UUID 隐藏域 + 门面快速查询 + `request_id` 唯一约束（冲突时整个事务回滚，扣减撤销）。
* **兜底**：`CHECK (stock >= 0)`、单据号/流水关联单据号唯一约束；死锁/锁超时在事务外重试 ≤ 3 次。
* **框架层约束**：`Product.stock` 映射为只读，代码无法通过实体写回库存。

详见 `docs/03-架构设计.md`。

## 8. 测试与验证

```bash
mvn test                          # 49 个测试：单元 / 集成 / Web 安全 / 前端页面 / 并发 / 反例实验（需 MySQL 测试库 inventory_test）
scripts/check-pages.sh            # 页面巡检：带登录会话请求全部页面，校验状态码与无模板错误
scripts/smoke-ui.sh               # 业务冒烟：curl 模拟表单完成新增→入库→出库→重复提交→库存不足→删除限制→核对
mysql -h127.0.0.1 -u"$DB_USER" -p"$DB_PASSWORD" inventory_db -e "SET @pid=NULL; SOURCE scripts/check-consistency.sql;"
perf/run-perf.sh                  # JMeter 压测全部场景（会在运行库写入大量压测数据，之后可执行 scripts/seed-demo.sh 恢复演示数据）
cd demo-video && npm install && node scripts/interact.mjs /tmp/ui   # 浏览器交互验收（Playwright，需系统 Chrome）
```

## 9. 前端界面（2026-11 重构）

参考 GitHub 上公开的前端设计 Skill（anthropics/skills 的 frontend-design、nextlevelbuilder/ui-ux-pro-max-skill、vercel-labs/web-interface-guidelines）重构了全部页面，
仍然是 **Thymeleaf + 原生 HTML/CSS/JS**，字体（IBM Plex，OFL）与图标（Lucide，ISC）均为本地文件，**离线可用**；后端接口、权限、业务逻辑未改动。

* 管理后台布局：侧栏导航（小屏为抽屉）+ 顶栏面包屑 + 用户角色
* 仪表盘：指标条、原生 SVG 近 7 日出入库柱状图（线性/对数切换）、库存健康度环形图、低库存预警
* 库存水位条：刻度线 = 预警阈值，低库存显示黄黑警示斜纹
* 出入库表单右侧“库存预览”，超量出库提前提示（最终以服务端原子更新为准）
* 右下角成功 Toast、常驻错误提示、自定义确认弹窗、空状态引导、403 页面

说明：`docs/10-前端重构说明.md`；设计规范：`docs/skill/frontend-design-SKILL.md`；重构前后截图：`docs/screenshots/redesign/`。

## 10. 演示视频

* 成品：`demo-video/out/demo.mp4`（1920×1080，H.264 + AAC，180.5 秒，中文配音 + 烧录中文字幕），字幕文件：`demo-video/out/demo.srt`；画面使用干净演示数据录制
* 制作方式：Playwright 驱动真实系统录屏 + Remotion 合成动画与字幕 + edge-tts 中文语音 + ffmpeg 混音封装，所有数据取自真实测试结果文件
* 重新制作：见 `docs/11-演示视频制作说明.md`

## 11. 目录与交付物

```
├── pom.xml / README.md / app.log（运行日志）
├── src/main/java/com/cumt/inventory/   源码（config / common / entity / repository / dto / service / controller）
├── src/main/resources/db/schema.sql    建表脚本        data.sql 初始化数据（默认账号、示例数据）
├── src/main/resources/templates|static Thymeleaf 页面、app.css、app.js
├── src/test/java/...                    自动化测试代码
├── perf/                                stock-out-burst.jmx、stock-load.jmx、run-perf.sh、analyze_jtl.py、results/ 原始结果
├── scripts/                             start/stop/init-db、check-pages、smoke-ui、check-consistency.sql、seed-demo.sh + seed_demo.py（演示数据）、screenshot/
├── demo-video/                          演示视频工程（Remotion + Playwright + edge-tts），out/demo.mp4、out/demo.srt
└── docs/
    ├── 01-需求规格说明书.md   02-开发计划.md   03-架构设计.md   04-数据库设计.md
    ├── 05-测试用例与评测标准.md   06-测试报告.md   07-性能测试报告.md
    ├── 08-课程报告素材.md   09-开发过程记录.md   10-前端重构说明.md   11-演示视频制作说明.md   12-演示数据说明.md   ppt-outline.md
    ├── skill/SKILL.md                    AI 开发 Skill
    ├── skill/frontend-design-SKILL.md    前端设计 Skill（提炼自 GitHub 公开 skill，注明来源）
    ├── test-results/                     测试原始输出
    └── screenshots/                      界面截图（redesign/ 为重构前后对比）
```
