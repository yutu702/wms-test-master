<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventory, getWarehouses, type InventoryItem, type Warehouse } from '@/api'

const keyword = ref('')
const warehouseId = ref<number | undefined>()
const locationCode = ref('')
const warehouses = ref<Warehouse[]>([])
const loading = ref(false)
const inventoryList = ref<InventoryItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

// 加载仓库列表（用于下拉筛选）
const loadWarehouses = async () => {
  try {
    const res = await getWarehouses()
    warehouses.value = res.data
  } catch {
    // 静默失败
  }
}

// 加载库存数据
const loadInventory = async () => {
  loading.value = true
  try {
    const res = await getInventory({
      keyword: keyword.value || undefined,
      warehouseId: warehouseId.value,
      locationCode: locationCode.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    inventoryList.value = res.data.list
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error('加载库存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

// 搜索防抖：监听 keyword 变化，300ms 后自动查询
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    page.value = 1
    loadInventory()
  }, 300)
})

// 仓库/库位筛选变化时重置页码
watch(warehouseId, () => {
  page.value = 1
  loadInventory()
})
watch(locationCode, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    page.value = 1
    loadInventory()
  }, 300)
})

// 分页变化
const handlePageChange = (newPage: number) => {
  page.value = newPage
  loadInventory()
}

// 低库存行样式：quantity < 10 红色高亮
const getRowStyle = ({ row }: { row: InventoryItem; rowIndex: number }) => {
  if (row.quantity < 10) {
    return { backgroundColor: '#fef0f0', color: '#f56c6c' }
  }
  return {}
}

onMounted(() => {
  loadWarehouses()
  loadInventory()
})
</script>

<template>
  <div>
    <h3>库存查询</h3>

    <!-- 搜索栏 -->
    <div style="display: flex; gap: 12px; margin-bottom: 16px">
      <el-input
        v-model="keyword"
        placeholder="搜索商品名称/SKU..."
        style="width: 300px"
        clearable
        @clear="() => { page = 1; loadInventory() }"
      />
      <el-select v-model="warehouseId" placeholder="选择仓库" clearable style="width: 200px">
        <el-option
          v-for="wh in warehouses"
          :key="wh.id"
          :label="wh.name"
          :value="wh.id"
        />
      </el-select>
      <el-input
        v-model="locationCode"
        placeholder="库位编码筛选..."
        style="width: 200px"
        clearable
        @clear="() => { page = 1; loadInventory() }"
      />
      <el-button type="primary" @click="page = 1; loadInventory()">查询</el-button>
    </div>

    <!-- 库存表格 -->
    <el-table :data="inventoryList" v-loading="loading" border stripe :row-style="getRowStyle">
      <el-table-column prop="productName" label="商品名称" />
      <el-table-column prop="sku" label="SKU" width="150" />
      <el-table-column prop="locationCode" label="库位编码" width="150" />
      <el-table-column prop="warehouseName" label="仓库" width="120" />
      <el-table-column prop="quantity" label="库存数量" width="120">
        <template #default="{ row }">
          <span :style="row.quantity < 10 ? 'color: #f56c6c; font-weight: bold' : ''">
            {{ row.quantity }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">
          {{ row.updatedAt?.replace('T', ' ').substring(0, 19) }}
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div style="margin-top: 16px; text-align: right">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <el-empty v-if="!loading && inventoryList.length === 0" description="暂无库存数据，请先完成入库操作" />
  </div>
</template>
