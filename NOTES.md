# WMS 开发笔记

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

## 任务 1 & 2 实现说明

### 任务 1：入库单创建

- 入库单号格式 `IN-YYYYMMDD-XXX`，使用 `synchronized` 保证单号唯一
- 使用 `@Transactional` 保证入库单和库存更新的原子性
- 使用 `SELECT ... FOR UPDATE` 悲观锁保证库存累加的并发安全
- `(product_id, location_code)` 唯一索引作为数据库层兜底

### 任务 2：库存查询

- JPQL JOIN 关联查询 Product/Location/Warehouse，避免 N+1 问题
- 支持关键字（商品名称/SKU）、仓库、库位编码三种筛选
- 数据库索引优化：inventory(product_id, location_code)、locations(warehouse_id, code)、products(sku, name)
- 前端搜索防抖 300ms + 仓库/库位筛选联动
