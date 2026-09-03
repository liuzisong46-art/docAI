# DocAI 代码改动建议

本文档专门记录代码阅读过程中发现的改动建议，作为后续优化、重构和面试复盘的长期清单。

## 使用约定

- 每条建议使用唯一编号，例如 `RAG-001`。
- 状态可选：`待评估`、`待实现`、`实现中`、`已完成`、`暂不处理`。
- 只记录当前代码能够证明的问题，不根据 README、注释或简历推测实现。
- 完成改动后，补充修改文件、验证方式和完成日期，不直接删除历史记录。

## 建议索引

| 编号 | 模块 | 建议 | 优先级 | 状态 |
| --- | --- | --- | --- | --- |
| RAG-001 | BM25 / Redis | 强化多用户数据隔离，避免全局扫描依赖后置过滤 | 高 | 待评估 |
| RAG-002 | RAG 索引更新 | 基于分片内容 Hash 实现最小可用的增量索引 | 高 | 待实现 |

## 详细建议

### RAG-001：强化 Redis 扫描的多用户数据隔离

- 模块：RAG / BM25 关键词检索
- 优先级：高
- 状态：待评估
- 相关文件：`ai-service/src/main/java/com/javaee/aiservice/rag/KnowledgeBase.java`

#### 当前实现

BM25 检索通过以下全局模式扫描文档和分片：

```java
scanKeys(CONTENT_PREFIX + "*", 1000);
scanKeys(SEGMENT_PREFIX + "*", 1000);
```

扫描得到候选 Key 后，再读取元数据并通过 `userId`、`knowledgeBaseId` 等条件过滤。

#### 存在的问题

1. 扫描阶段会枚举共享 Redis 中所有用户的文档和分片 Key。
2. 用户隔离依赖上层调用方正确传入 `filters`，服务层没有强制保证。
3. 存在使用空过滤条件的检索重载，误调用时可能返回跨用户数据。
4. 数据量增长后，全局扫描和逐条元数据读取会增加 Redis 压力与检索延迟。

#### 建议方案

优先采用租户化索引结构，将用户和知识库信息加入 Redis Key 或独立索引集合，例如：

```text
content:{userId}:{knowledgeBaseId}:{documentId}
segment:{userId}:{knowledgeBaseId}:{segmentId}
```

检索时只扫描当前作用域：

```text
content:{userId}:{knowledgeBaseId}:*
segment:{userId}:{knowledgeBaseId}:*
```

同时建议：

1. 在服务层从可信身份上下文获取 `userId`，不要完全信任调用方传入的用户 ID。
2. 面向用户请求的检索方法强制要求用户和知识库范围，不提供空过滤重载。
3. 元数据过滤作为第二层防护继续保留，形成“Key 范围限制 + 元数据校验”的双重隔离。
4. 数据规模较大时，使用按租户维护的文档/分片集合或倒排索引，避免依赖全局 `SCAN`。

#### 预期收益

- 降低跨用户数据泄露风险。
- 减少无关 Key 扫描和元数据读取。
- 明确服务层的多租户安全边界。
- 提升大数据量下 BM25 检索的可扩展性。

#### 验证建议

1. 创建两个用户及各自的知识库和文档。
2. 用户 A 查询时，断言候选集和最终结果均不包含用户 B 的文档。
3. 构造缺少用户过滤条件的调用，断言服务拒绝执行，而不是退化为全局检索。
4. 删除或伪造元数据后，断言数据不会绕过隔离规则。

#### 实施记录

- 修改文件：待补充
- 测试结果：待补充
- 完成日期：待补充

### RAG-002：基于分片内容 Hash 实现最小可用的增量索引

- 模块：RAG / 文档索引更新
- 优先级：高
- 状态：待实现
- 相关文件：`ai-service/src/main/java/com/javaee/aiservice/rag/KnowledgeBase.java`、`ai-service/src/main/java/com/javaee/aiservice/rag/VectorStore.java`

#### 当前实现

`KnowledgeBase.updateDocument` 当前采用先删除、后重建：

```java
removeDocument(documentId);
addDocumentWithSegment(documentId, content, metadata, strategyType);
```

文档发生少量修改时，系统仍会删除全部旧分片，对整篇新文档重新分段、调用 Embedding 并写入 Redis 与 HNSW。因此当前能力属于“索引更新/全量重建”，不是严格的分片级增量索引。

#### 存在的问题

1. 未变化分片也会重复调用 Embedding，增加更新延迟和模型费用。
2. 先删除旧索引再写入新索引，新索引构建失败时旧索引已经丢失。
3. 当前分片 ID 依赖位置序号，文档前部变化可能导致后续分片 ID 整体变化。
4. Redis 分片正文、元数据和 HNSW 多处写入缺少事务或补偿机制。

#### 建议方案

实现最小可用的分片级增量更新，不先调用 `removeDocument`：

1. 对标准化后的分片正文计算 SHA-256 `contentHash`，并写入分片元数据。
2. 读取 `doc_segments:{documentId}` 对应的旧分片列表，建立 `contentHash -> segmentId` 映射。
3. 对新文档重新分段并计算 Hash，与旧 Hash 集合求差：
   - Hash 未变化：复用旧分片原文和向量，仅按需更新顺序、标题等元数据；
   - 新增 Hash：保存分片正文，调用 Embedding，并写入 Redis 与 HNSW；
   - 失效 Hash：删除旧分片正文、元数据和 HNSW 节点。
4. 新增分片全部写入成功后，再更新文档原文及 `doc_segments:{documentId}` 映射，降低更新失败造成的索引空窗风险。
5. 同一文档存在重复正文时，使用 `contentHash + occurrence` 区分多次出现的相同分片。
6. 分段策略或 Embedding 模型版本变化时，不复用旧向量，改走全量重建。

建议的核心流程：

```text
读取旧分片及 Hash
→ 新文档重新分段并计算 Hash
→ 求出 unchanged / added / removed
→ added 执行 Embedding 并写入
→ unchanged 复用向量并更新必要元数据
→ 切换文档分片映射
→ 删除 removed
```

最小改造可新增以下职责方法：

```java
calculateContentHash(content)
loadOldHashToId(documentId)
storeAddedSegments(added)
updateUnchangedMetadata(unchanged)
deleteRemovedSegments(removed)
```

#### 预期收益

- 仅对新增或修改分片调用 Embedding，降低更新成本和延迟。
- 复用未变化向量，使简历中的“增量索引”与实际代码一致。
- 避免更新开始时立即删除全部旧索引，降低失败时不可检索的风险。
- 为后续版本化索引、失败补偿和并发更新控制建立基础。

#### 验证建议

1. 文档仅修改一个分片时，断言只有该分片触发 Embedding。
2. 新增、删除各一个分片时，断言 HNSW 与 Redis 分别新增、删除对应 ID。
3. 未变化分片在更新前后保持相同 ID 和向量。
4. 模拟新增分片写入失败，断言旧文档分片映射仍可用于检索。
5. 构造内容完全相同的重复分片，断言不会因 Hash 相同相互覆盖。
6. 修改分段策略或 Embedding 模型版本，断言触发全量重建。

#### 实施记录

- 修改文件：待补充
- 测试结果：待补充
- 完成日期：待补充

## 新建议模板

复制下面的结构继续追加：

```markdown
### 模块编号：建议标题

- 模块：
- 优先级：高 / 中 / 低
- 状态：待评估
- 相关文件：

#### 当前实现

#### 存在的问题

#### 建议方案

#### 预期收益

#### 验证建议

#### 实施记录

- 修改文件：待补充
- 测试结果：待补充
- 完成日期：待补充
```
