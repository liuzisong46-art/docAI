# DocAI 项目设计与面试事实底稿

> 用途：后续回答项目设计、技术选型和面试问题前，先查阅本文件，再按需核对当前代码。
>
> 原则：当前代码高于 README、注释和简历描述；区分“已实现”“预留骨架”“建议改进”，不得捏造生产规模或性能数据。

## 1. 项目定位

DocAI 是一个面向文档管理、协作和智能分析的 Java 项目，覆盖：

- 用户与身份认证；
- 原始文件上传、下载和对象存储；
- 文档解析、正文存储、版本管理和协作权限；
- 摘要、关键词提取、分析和对话；
- RAG 知识库索引、检索和问答；
- 多种 Agent 执行模式及任务状态管理；
- RabbitMQ 异步 AI 任务；
- MCP 工具接入。

主要用户是需要集中管理、协作编辑和检索团队文档的知识工作者或团队。项目解决的问题可以概括为：文件分散、版本难追踪、协作权限难管理、长文档阅读成本高、团队知识难以通过自然语言检索。

## 2. 仓库模块与职责

### 2.1 可独立运行的服务

| 模块 | 实际职责 |
|---|---|
| `gateway-service` | 对用户、文件、文档和 AI 请求进行统一路由；承接认证拦截、身份透传等网关职责。支持 Nacos 服务发现配置，Docker 配置也使用容器服务名直连。 |
| `user-service` | 用户注册、登录、认证和用户信息相关能力。 |
| `file-service` | 原始文件上传/下载；MinIO 保存原文件；MySQL 保存文件元数据；发布并消费文件 RabbitMQ 事件。 |
| `document-service` | 通过 file-service 获取原文件；同步解析文本；MinIO 保存当前正文；MySQL 保存文档元数据、历史版本和协作权限。 |
| `ai-service` | 模型调用、提示词、Agent、RAG、Redis 记忆/任务状态、RabbitMQ 异步 AI 任务。 |
| `mcp-server` | 将知识库检索、文档查询和工具目录封装成 MCP Tools；通过 HTTP 代理调用 AI 服务。 |

### 2.2 非微服务模块

- `common`：公共 Java 依赖模块，不是独立服务。
- `vue-test-app`：前端项目。

### 2.3 服务通信

- 外部请求：客户端 → Gateway → 各业务服务。
- 文档取原文件：document-service 通过 Feign/HTTP 调用 file-service。
- 文件事件和异步 AI 任务：RabbitMQ。
- MCP 调用：MCP 客户端 → mcp-server → HTTP → ai-service。
- Redis：AI 会话、短期记忆、任务状态、RAG 向量和元数据。
- MySQL：业务元数据、文档版本、协作关系。
- MinIO：原始文件、当前解析文本。

## 3. 上传到知识库问答的实际链路

### 3.1 上传原始文件

1. 用户请求经过 Gateway 到 file-service。
2. file-service 生成唯一对象 Key。
3. 原始文件写入 MinIO。
4. 文件名、大小、类型、对象 Key 等元数据写入 MySQL。
5. 接口返回 `fileId`。
6. 上传事件发送到 RabbitMQ。

边界：file-service 的上传、下载、处理、删除事件消费者目前主要记录日志，没有真正触发解析、审计或清理业务。

### 3.2 创建业务文档

1. 客户端携带 `fileId` 调用 document-service 创建文档。
2. document-service 写入文档元数据，并为创建者写入 owner 协作记录。
3. 通过 file-service 获取文件名和完整文件字节。
4. 在当前请求线程同步解析文本。
5. 当前纯文本写入用户 Bucket 下的 `document-content/{documentId}.txt`。
6. MySQL 保存摘要等文档元数据。
7. `document_version` 保存版本 1 的全文快照。

边界：解析或 MinIO 写入异常在创建流程中会被捕获，文档可能降级为只有元数据和空正文；没有解析状态、自动重试或补偿任务。

### 3.3 建立知识索引

当前上传、创建文档、知识索引并非自动串联。客户端或上层业务需要显式调用 ai-service 的索引接口，并提交 `documentId` 与正文。

索引步骤：

1. 根据固定长度、章节、语义或混合策略分段。
2. 增加用户 ID、知识库 ID、文档 ID 等元数据。
3. 使用模型厂商 SDK 生成 Embedding。
4. 将向量和元数据持久化到 Redis。
5. 将向量加入 JVM 内存 HNSW 索引。
6. 使用内容哈希判断内容是否变化。

“增量索引”的准确含义：内容哈希未变时复用；内容变化时删除旧分段并全量重建，不是 chunk 级差异更新。

异步知识索引使用本地固定线程池，不是 RabbitMQ。

### 3.4 RAG 问答

1. 根据当前用户和知识库 ID 构造元数据过滤条件。
2. 将问题生成查询向量。
3. HNSW 执行向量召回。
4. 执行简化版 BM25 关键词召回。
5. 合并候选结果。
6. 执行本地或外部重排序。
7. 取前几个片段拼接上下文。
8. 调用模型生成回答。
9. 返回问题、上下文、回答和来源分段 ID。

边界：

- BM25 是项目内的简化实现，不是完整标准搜索引擎实现。
- 当前混合结果以向量结果优先加入，候选截断后 BM25 结果可能难以进入最终 topK，不应宣称已经实现成熟的分数融合。
- `strategyType` 在部分查询方法中主要用于日志，并未真正改变检索路径。

## 4. Agent 设计事实

### 4.1 已实现能力

- 知识检索型执行；
- ReAct 执行模式；
- Plan-Execute 执行模式；
- 对话、规划、工具调用、知识检索等能力组合；
- 最大执行步数/工具调用次数限制；
- 执行轨迹和任务快照；
- 危险操作审批；
- 失败步骤重试、任务继续和取消；
- WebSocket/STOMP 进度广播；
- 用户身份和任务隔离。

Agent 编排主体是项目自定义代码，不是 Spring AI 自动生成的执行框架。

### 4.2 多轮记忆和任务状态

- Redis 保存会话上下文、短期记忆和 Agent/异步任务状态。
- Key 中包含会话或任务标识，并结合用户身份做访问隔离。
- TTL 控制数据生命周期。
- 异步 AI 任务状态为：`queued → running → success/failed`。
- RabbitMQ 消费线程会重建 Spring Security 上下文，执行完成后在 `finally` 中清理，避免线程复用造成身份泄漏。

## 5. RabbitMQ 设计事实

### 5.1 文件事件

- file-service 声明持久化 Topic Exchange 和上传、下载、处理、删除队列。
- Controller 在相应操作后发布 Map 形式事件。
- 消费者目前只解析字段和记录日志。
- 文件上传本身在发送事件前已经同步写入 MinIO，不是由 RabbitMQ 完成上传。

### 5.2 异步 AI 任务

完整链路：

1. Controller 提交摘要、关键词、分析、对话或 Agent 任务。
2. Redis 写入 `queued` 状态并设置 TTL。
3. 任务消息发送到 RabbitMQ。
4. 消费者将状态更新为 `running`。
5. 按类型调用对应 AI 或 Agent 能力。
6. 成功写入 `success + result`；失败写入 `failed + error`。
7. 客户端通过 `jobId` 查询，并校验任务所有者或管理员身份。

可靠性边界：

- 未看到 Publisher Confirm、Returns Callback 和 Transactional Outbox。
- 未配置业务死信队列。
- AI 消费异常被捕获并标记 failed，没有重新抛出，因此不会依靠 RabbitMQ 自动重试。
- Redis 保存成功但发送失败时，任务可能长期停留在 queued。

## 6. MinIO、MySQL 与版本管理

### 6.1 存储划分

- file-service MinIO：原始文件。
- document-service MinIO：当前解析文本。
- file-service MySQL：文件元数据。
- document-service MySQL：文档元数据、历史版本、协作权限。
- 历史正文当前以 `LONGTEXT` 完整快照存入 `document_version`，不是 MinIO 版本引用。

### 6.2 文档更新

1. 查询文档并校验写权限。
2. 从 MinIO 读取更新前正文。
3. 将旧状态保存为 MySQL 历史版本。
4. 当前版本号加一并更新文档元数据。
5. 如果提交新正文，则覆盖 MinIO 固定对象 Key。
6. 更新本地规则摘要并返回。

已知边界：

- 只修改元数据也会生成版本。
- 版本创建人取了文档原始创建者，不是本次修改者。
- 正文更新会执行两次 MySQL UPDATE。
- 纯元数据更新时，响应中的正文为 null。
- MinIO 读取异常返回 null，更新流程可能保存空历史版本并继续覆盖。
- MySQL 本地事务不能自动回滚 MinIO 写入。

### 6.3 版本恢复

- 恢复前先保存当前快照，因此恢复操作可逆。
- 恢复后版本号继续递增，不会倒退为目标历史版本号。
- 当前恢复标题、摘要、关键词和正文，不恢复分类与标签。

## 7. 协作权限模型

- 角色：`owner`、`editor`、`viewer`。
- 读权限：三种角色都有。
- 写权限：owner、editor。
- 真正的唯一所有者依据是 `document.user_id`。
- 只有文档主表创建者能够给协作者授权。
- 权限表中的 owner 具有读写权，但不具备继续授权能力。
- `document_access` 对 `(document_id, user_id)` 建有唯一索引。
- 当前角色缺省值为 editor，最小权限角度更适合 viewer 或强制显式传值。
- 当前表结构没有权限状态、有效期和授权人审计字段。

## 8. Spring AI 的真实使用范围

这是回答时必须特别谨慎的部分。

### 8.1 当前代码实际使用

- ai-service 的 `PromptEngineeringService` 使用 Spring AI `PromptTemplate` 渲染摘要、关键词、问答、规划和反思提示词。
- mcp-server 使用 Spring AI MCP Server Starter。
- MCP Tools 通过 Spring AI `@Tool` 声明。
- `MethodToolCallbackProvider` 用于注册 MCP 工具。

### 8.2 不能错误归因给 Spring AI 的能力

- 主要模型调用由 DashScope Java SDK 和 OpenAI 兼容 HTTP/`RestTemplate` 实现。
- Embedding 使用厂商 SDK。
- Agent 编排是自研。
- RAG 分段、Redis 持久化、HNSW、BM25 和重排序是自研。
- Redis 记忆与 RabbitMQ 任务状态是自研。

虽然 ai-service 的 POM 引入了 Spring AI Core、OpenAI Starter 和 Redis Vector Store，`AiConfig` 注释也提到自动配置，但当前业务代码没有实际使用 `ChatClient`、`ChatModel`、`EmbeddingModel` 或 Spring AI Redis Vector Store。

因此简历中“AI 服务基于 Spring AI 构建”偏重。准确表述：

> AI 服务整合 Spring AI 提示词模板与 MCP Server 能力，结合厂商 SDK 实现模型调用，并自研 Agent 编排与 RAG 检索链路。

## 9. MCP Server 设计事实

- mcp-server 是独立模块。
- 对外工具包括知识库检索、文档列表、文档元数据、Agent Skills/工具目录。
- 工具方法通过 HTTP 调用 ai-service，不复制 RAG 实现。
- Bearer Token 使用过滤器写入 ThreadLocal，请求结束后清理，再转发给 AI 服务。
- 采用 Spring AI MCP WebMVC Starter 和 SSE 相关配置。

## 10. 项目规模与性能口径

当前是个人项目/工程验证阶段，没有真实生产用户流量，也没有形成可写入简历的标准压测数据。

回答时不得虚构：

- 文档总量；
- 向量总量；
- QPS；
- P95/P99；
- 并发用户数；
- 准确率提升百分比。

可以说明已经完成核心功能与集成链路验证，并能指出当前扩展瓶颈：

- HNSW 为单实例 JVM 内存索引，启动时从 Redis 扫描重建。
- 简化 BM25 会扫描 Redis 内容，不适合大规模语料。
- 原始文件和正文多处使用完整 byte[]/readAllBytes，存在大文件内存压力。
- 模型调用延迟和限流是整体吞吐瓶颈之一。

## 11. 可用于“实际问题”的案例

### Redis 持久化向量与内存 HNSW 重启不一致

问题：服务重启后 Redis 向量仍在，但 JVM 内存 HNSW 为空，检索返回空结果。

解决：

1. `@PostConstruct` 启动预热；
2. 使用 Redis SCAN 分批扫描向量 Key；
3. 恢复向量维度并重建 HNSW；
4. 单条坏向量记录日志并跳过；
5. Redis 不可用时允许服务启动，首次写入时延迟建索引；
6. 运行期写入、更新、删除同步维护 Redis 和 HNSW，并使用读写锁保护内存索引。

结论：Redis 是持久化数据源，HNSW 是可重建的查询加速层。

## 12. 如果重新设计的优先级

1. 把上传、解析、索引改成有状态的可靠事件工作流。
2. 增加 Outbox、Publisher Confirm、幂等、重试和死信队列。
3. 明确文档处理状态，补齐 MySQL 与 MinIO 的补偿机制。
4. 历史正文改存 MinIO，MySQL 只保存版本元数据与对象引用。
5. 数据量扩大后迁移到专业向量/搜索系统，实现标准 BM25、RRF 分数融合和独立 Rerank。
6. 统一所有者语义，补充权限撤销、转让、有效期和审计。
7. 接入统一 traceId、指标和链路追踪。

## 13. 推荐的项目一句话表述

> DocAI 将原始文件存储、文档解析与版本协作、AI Agent、RAG 问答和 MCP 工具接入组合成一套文档智能处理平台；当前代码完成了核心功能链路，但上传到索引尚未自动编排，消息可靠性与跨存储一致性仍是后续完善重点。

## 14. 回答项目问题时的检查清单

1. 先判断问题问的是当前实现、设计目标还是未来改进。
2. 只把当前代码能证明的能力说成“已实现”。
3. 不把文件 RabbitMQ 日志消费者说成真实异步解析。
4. 不把本地线程池异步索引说成 RabbitMQ 索引任务。
5. 不把简化 BM25 和顺序合并说成成熟混合检索融合。
6. 不把自研 Agent/RAG/Redis 记忆归因给 Spring AI。
7. 不虚构生产数据和性能指标。
8. 被问到不足时，说明边界以及具体改进方案，不回避问题。
