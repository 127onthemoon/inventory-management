---
name: frontend-design-admin
description: 为“服务端渲染（Thymeleaf）+ 原生 HTML/CSS/JS”的中文管理后台做界面设计与重构时使用。提炼自 GitHub 上公开的 AI 前端设计 skill 与界面规范，覆盖设计方向、令牌体系、组件、可访问性、交互反馈与验收清单。
---

# 前端设计 Skill（管理后台版）

> 本文件是对下列公开资料的**学习笔记与提炼**，不是原文复制；各条规则后括号内标注了出处。
> 读取时间：2026-11-16。仅读取学习，未向任何仓库提交内容。
>
> | 简称 | 仓库 / 文件 | Star（读取时） | 许可 |
> |---|---|---|---|
> | **[A] frontend-design** | [anthropics/skills · skills/frontend-design/SKILL.md](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md) | 约 19.5 万 | 见仓库 LICENSE.txt |
> | **[U] ui-ux-pro-max** | [nextlevelbuilder/ui-ux-pro-max-skill · .claude/skills/ui-ux-pro-max/SKILL.md](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) 及 `references/quick-reference.md`，并实际运行其 `scripts/search.py --design-system` | 约 15 万 | MIT |
> | **[V] Web Interface Guidelines** | [vercel-labs/web-interface-guidelines · AGENTS.md](https://github.com/vercel-labs/web-interface-guidelines)（[vercel-labs/agent-skills · web-design-guidelines](https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines) 引用的规范源） | 约 1.2 千 / 3.4 万 | 前者 MIT；agent-skills 仓库未在 GitHub 声明许可 |

## 1. 先定主题，再定样式

1. 先写清楚**主题、用户、页面的主要任务**；视觉选择从主题所在行业的“物料与行话”里来（[A]）。
   - 本项目：仓储/库存 → 钢梁、混凝土地坪、托盘标签、地面黄色标线、黄黑警示带。
2. 产出一个**紧凑的令牌方案**再写代码：4–6 个具名颜色、字体及用途、布局草图、设计原则（[A]）。
3. 对照“AI 生成界面的常见套路”自查，若方案与之雷同就改（[A]）：
   奶油底 + 衬线大标题 + 陶土色强调；黑底 + 荧光绿；报纸式细线排版；**同一圆角、同一灰阴影的卡片堆砌**；每个标题上方的全大写小标签、`A · B · C` 元信息串、按钮文字末尾加 `→`。
4. **只在一个地方大胆**，其余保持克制；上线前“摘掉一件配饰”（[A]）。

## 2. 令牌体系（Design Tokens）

- 颜色用 CSS 变量，组件里不写裸十六进制（[U] typography & color）。语义色（成功/警告/危险/信息）独立定义，白底对比度 ≥ 4.5:1（[U][V]）。
- 边框、阴影、文字向背景色相微调；阴影分环境光 + 直射光两层（[V] Design）。
- 间距用 4px 体系；字号用固定阶梯；正文 14–16px、行高 1.5 左右（[U]）。
- 嵌套圆角：子元素圆角 ≤ 父元素（[V]）。
- 数字对比处使用 `font-variant-numeric: tabular-nums`（[V]）。
- 字体自带或走系统字体，**不依赖外网 CDN**（离线演示约束）；中文用系统黑体栈。

## 3. 布局与导航

- 管理后台：固定侧栏（图标 + 文字，二者缺一不可）+ 顶栏（面包屑/当前位置 + 用户 + 退出）（[U] nav-label-icon）。
- 当前位置要有非颜色单一的提示（高亮底色 + 定位条 + `aria-current="page"`）。
- 响应式：≥1200 四列指标；≤1024 侧栏变抽屉（遮罩 + Esc 关闭）；≤640 单列表单，输入框字号 ≥16px 防止 iOS 放大（[V] Targets & Input）。
- 页面不得出现横向滚动；宽表格只在表格容器内滚动（[U] Layout & Responsive）。
- 链接导航必须是 `<a>`，筛选条件和分页体现在 URL 里（[V] State & Navigation）。

## 4. 组件规则

| 组件 | 规则 | 出处 |
|---|---|---|
| 图标 | 用一套 SVG 图标（如 Lucide），统一线宽；**不用 emoji 当图标**；装饰性图标 `aria-hidden` | [U] no-emoji-icons / icon-style-consistent |
| 按钮 | 文案说清楚动作（“确认出库”而非“提交”）；提交后禁用并显示加载状态，保留原文案 | [A] writing、[V] Forms |
| 表单 | 可见的 label（`for` 关联）；错误紧贴字段；占位符以 `…` 结尾并给示例 | [V] Forms、[U] Forms & Feedback |
| 表格 | 表头弱化、数字右对齐、行悬停；长文本截断（`min-width:0` + ellipsis） | [V] Content Handling |
| 状态 | 颜色 + 文字/图标双重表达（如“● 低库存”），不只靠颜色 | [U] color-not-only、[V] |
| 空状态 | 说明原因 + 给下一步（按钮）；“空页面是行动邀请” | [A] writing、[V]、[U] empty-states |
| 提示 | 成功用 Toast（`aria-live=polite`，3–5 秒自动消失，不抢焦点）；错误常驻在页面顶部直到处理 | [U] toast-dismiss / toast-accessibility |
| 危险操作 | 必须二次确认；确认框有取消、Esc 关闭、焦点归还 | [V] Feedback、[U] confirmation-dialogs / modal-escape |
| 图表 | 图表类型匹配数据（趋势/对比→柱、占比→环）；有图例；提供表格文字替代；空数据有提示 | [U] Charts & Data |

## 5. 动效

- 只为“说明发生了什么”而动：抽屉滑出、弹窗出现、Toast 进出；不做每个区块的淡入上滑（[A]）。
- 只动 `transform` / `opacity`，不写 `transition: all`；尊重 `prefers-reduced-motion`（[V] Animation）。

## 6. 文案

- 站在用户角度命名（“出库管理”而不是“OutboundController”）；主动语态；同一动作全流程同名（[A]）。
- 错误要说清楚发生了什么、怎么办，不道歉、不含糊（[A]）：例如“库存不足：商品【X】无法出库 999 件”。
- 使用 `…` 而不是 `...`（[V]）。

## 7. 可访问性底线

键盘可达、可见焦点（`:focus-visible`）、“跳到主要内容”链接、语义标签优先于 ARIA、图标按钮有 `aria-label`、对比度达标（[V][U]）。

## 8. 交付前验收清单

- [ ] 截图自查每个页面（桌面 1440、移动 390），无错位、无乱码、无横向溢出（[A] 自我批评：截图复查）
- [ ] 没有 emoji 图标；图标风格统一
- [ ] 所有危险操作有确认；成功/失败都有反馈
- [ ] 空状态、长文本、低库存等边界情况都有设计
- [ ] 无外网资源（字体、图标、脚本均为本地文件）
- [ ] 后端接口、权限、业务逻辑未改变；原有自动化测试全部通过
