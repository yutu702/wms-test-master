# WMS 开发笔记

> 本文档记录 WMS 仓库管理系统各任务的实现方案。

---

## 任务 1：入库单创建

### 实现概述

实现入库单创建的前后端完整功能，包括入库单号自动生成、多行明细录入、库存自动累加。

### 后端实现

**API 设计：**
- `POST /api/inbound-orders` — 创建入库单

**关键技术方案：**

| 功能点 | 方案 |
|--------|------|
| 入库单号生成 | `IN-YYYYMMDD-XXX` 格式，使用 `synchronized` 保证线程安全 |
| 事务保证 | `@Transactional` 注解，入库单+明细+库存更新在同一事务 |
| 库存累加并发安全 | `SELECT ... FOR UPDATE` 悲观锁，防止并发入库丢失更新 |
| 数据库兜底 | `(product_id, location_code)` 唯一索引，防止重复记录 |
| 异常处理 | 商品/库位不存在时抛出 404，参数校验失败返回 400 |

**涉及文件：**
- `backend-java/src/main/java/com/wms/service/InventoryService.java`
- `backend-java/src/main/java/com/wms/controller/InventoryController.java`
- `backend-java/src/main/java/com/wms/entity/InboundOrder.java`
- `backend-java/src/main/java/com/wms/entity/InboundOrderItem.java`
- `backend-java/src/main/java/com/wms/repository/InventoryRepository.java`

### 前端实现

**页面：** `frontend-vue/src/views/InboundView.vue`

**交互设计：**
- 供应商名称输入
- 多行明细：商品下拉搜索 → 仓库选择 → 库位级联选择 → 数量输入
- 支持添加/删除明细行
- 提交后显示入库单号

---

## 任务 2：库存查询

### 实现概述

实现库存列表的分页查询，支持多维度筛选，优化查询性能。

### 后端实现

**API 设计：**
- `GET /api/inventory?keyword=&warehouseId=&locationCode=&page=&pageSize=` — 库存分页查询

**关键技术方案：**

| 功能点 | 方案 |
|--------|------|
| 关联查询 | JPQL `JOIN` Product/Location/Warehouse，避免 N+1 问题 |
| 多维度筛选 | 支持关键字（名称/SKU模糊匹配）、仓库ID、库位编码 |
| 分页 | Spring Data `Pageable`，返回 total/page/pageSize |
| 索引优化 | `inventory(product_id, location_code)`、`locations(warehouse_id, code)`、`products(sku, name)` |

**索引定义示例：**
```java
@Table(name = "inventory",
    indexes = {
        @Index(name = "idx_inventory_product_id", columnList = "product_id"),
        @Index(name = "idx_inventory_location_code", columnList = "location_code")
    })
```

### 前端实现

**页面：** `frontend-vue/src/views/InventoryView.vue`

**关键功能：**
- 搜索防抖 300ms（`watch` + `setTimeout`）
- 仓库下拉筛选 + 库位编码输入筛选
- 低库存行高亮（`quantity < 10` 红色背景）
- 后端分页

---

## 任务 3：Bug 修复

### Bug 1：商品删除未校验关联库存（后端）

**问题描述：**
`ProductService.delete()` 方法在删除商品时，没有检查该商品是否存在关联的库存记录（`inventory` 表）。
直接删除商品会导致 `inventory` 表中的 `product_id` 指向一个不存在的商品，造成数据孤立。

**修复方式：**
在 `ProductService.delete()` 中增加库存关联校验：
1. 注入 `InventoryRepository` 依赖
2. 删除前调用 `inventoryRepository.existsByProductId(id)` 检查是否存在关联库存
3. 若存在关联库存，抛出 `BusinessException(400, "该商品存在关联库存记录，无法删除。请先清空库存后再删除商品")`

**涉及文件：**
- `backend-java/src/main/java/com/wms/service/ProductService.java`
- `backend-java/src/main/java/com/wms/repository/InventoryRepository.java`（新增 `existsByProductId` 方法）

---

### Bug 2：编辑商品后列表页码重置（前端）

**问题描述：**
`ProductsView.vue` 的 `handleSubmit()` 函数中，在编辑商品提交成功后，
执行了 `currentPage.value = 1`，导致用户从第 N 页点击编辑 → 提交后，
列表跳回第 1 页，用户体验差。

**修复方式：**
区分「新增」和「编辑」操作：
- 新增商品（`form.id === 0`）：重置到第 1 页（因为新商品可能不在当前页）
- 编辑商品（`form.id !== 0`）：保留当前页码，刷新数据

**涉及文件：**
- `frontend-vue/src/views/ProductsView.vue`

---

## 选做 A：出库单 + 库存扣减

### 实现概述

实现出库单创建功能，核心是库存扣减的并发安全控制。

### 并发控制方案选择

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **悲观锁** `SELECT ... FOR UPDATE` | 强一致性，实现简单 | 锁持有时间长，吞吐量低 | 并发不高、强一致性要求 |
| **乐观锁** CAS 重试 | 无锁，吞吐量高 | 高并发下重试多，需循环 | 冲突概率低的场景 |
| **纯原子 UPDATE** ✅ | 无锁、无重试、最简单 | 仅适用于简单扣减 | 库存扣减场景 |

**最终方案：纯数据库原子 UPDATE**

```sql
UPDATE inventory 
SET quantity = quantity - :qty, updated_at = NOW()
WHERE product_id = :productId 
  AND location_code = :locationCode 
  AND quantity >= :qty
```

**方案优势：**
1. 纯数据库操作，无需应用层加锁
2. `WHERE quantity >= :qty` 保证不会超卖
3. 返回受影响行数，0 表示库存不足
4. 事务内多条明细任一失败，全部回滚

### 后端实现

**API 设计：**
- `POST /api/outbound-orders` — 创建出库单

**并发安全措施：**
1. 纯原子 UPDATE 扣减库存（`UPDATE ... WHERE quantity >= :qty`），数据库层面保证不超卖
2. 明细按 `productId + locationCode` 排序后顺序扣减，防止多订单并发时死锁
3. `@Transactional` 保证出库单+明细+库存扣减的原子性，任一失败全部回滚

**涉及文件：**
- `backend-java/src/main/java/com/wms/service/OutboundService.java`
- `backend-java/src/main/java/com/wms/controller/OutboundController.java`
- `backend-java/src/main/java/com/wms/entity/OutboundOrder.java`
- `backend-java/src/main/java/com/wms/entity/OutboundOrderItem.java`
- `backend-java/src/main/java/com/wms/repository/InventoryRepository.java`（新增 `deductIfSufficient` 方法）

### 前端实现

**页面：** `frontend-vue/src/views/OutboundView.vue`

**交互设计：**
- 与入库单类似的表单结构
- 商品选择 → 仓库/库位选择 → 数量输入
- 支持多行明细

---

## Git 提交记录

```
0b5cca6 - 优化选做 A：排除死锁排序风险
22f60c5 - OutboundOrderCreateRequest补全校验注解
9b3014c - 选做 B：单元测试
4541cf8 - 选做 A（出库单 + 库存扣减）：出库（纯原子UPDATE方案），入库（悲观锁方案）
57b7793 - 任务 3-2-前端 Bug：商品列表页切换页码后，编辑某条商品返回列表时跳回了第 1 页
a2d2cd0 - 任务 3-1-后端 Bug：商品删除接口没有校验该商品是否有关联库存，导致删除后库存数据孤立
03c30f9 - 任务 2：库存查询前后端实现
608453d - 任务一：入库单创建前后端实现
c4f2406 - 切换至H2内存数据库，便于本地开发
b199fc1 - 初始化代码：WMS仓库管理系统模板
```

---

## AI 使用记录

### 使用的 AI 工具

**Qoder**（IDE 内置 AI 编程助手），贯穿整个开发流程，用于代码生成、方案设计、Bug 定位、Code Review 和单元测试编写。

### AI 帮我解决了什么问题？

**选做 A 出库单库存扣减的并发方案设计。**

在实现出库单库存扣减时，我一开始没有明确的并发控制思路。AI 帮我梳理了三种可选方案并做了对比分析：

| 方案 | 优点 | 缺点 |
|------|------|------|
| 悲观锁 `SELECT ... FOR UPDATE` | 强一致性 | 锁持有时间长，吞吐量低 |
| 乐观锁 CAS 重试 | 无锁，吞吐量高 | 高并发下重试多 |
| 纯原子 UPDATE | 无锁、无重试、最简单 | 仅适用于简单扣减 |

AI 推荐了「纯原子 UPDATE」方案，理由是本场景只是简单的库存扣减，不需要先读后写的复杂流程，一条 `UPDATE ... WHERE quantity >= :qty` 就能同时完成扣减和防超卖，由数据库引擎保证原子性。最终采纳该方案，实现简洁且经过测试验证事务回滚正常。

### AI 生成的代码有什么问题？如何发现和修复的？

**问题：`OutboundOrderCreateRequest` 内部类缺少参数校验注解。**

AI 生成出库单 DTO 时，外层 `OutboundOrderCreateRequest` 正确添加了 `@NotBlank`、`@NotEmpty` 等校验注解，但内部静态类 `OutboundItemRequest` 的三个字段（`productId`、`quantity`、`locationCode`）没有添加任何校验注解。对比入库单的 `InboundItemRequest`（有 `@NotNull`、`@Min`、`@NotBlank`），发现出库 DTO 漏掉了。

**发现方式：** 在最终 Code Review 阶段，逐一对比入库和出库的 DTO 校验逻辑时发现。

**修复方式：** 为 `OutboundItemRequest` 补全了 `@NotNull`、`@Min(1)`、`@NotBlank` 注解，防止 `productId=null` 等非法请求穿透到 Service 层导致 NPE。已单独提交：`OutboundOrderCreateRequest补全校验注解`。

**教训：** AI 生成嵌套类结构时容易遗漏内层的细节，不能只检查外层类就认为校验完整，需要对每一层 DTO 逐一核对。

