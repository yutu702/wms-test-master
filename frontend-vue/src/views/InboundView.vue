<script setup lang="ts">
/**
 * 入库管理页
 * 
 * 功能：
 * 1. 表单：供应商名称 + 入库明细列表
 * 2. 每行明细：选择商品（下拉搜索）→ 选择仓库 → 选择库位 → 输入数量
 * 3. 支持添加/删除明细行
 * 4. 提交按钮（调用 createInboundOrder API）
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createInboundOrder,
  getProducts,
  getWarehouses,
  getLocations,
  type Product,
  type Warehouse,
  type Location,
} from '@/api'

const supplierName = ref('')
const submitting = ref(false)

// 商品列表
const products = ref<Product[]>([])
const productLoading = ref(false)

// 仓库列表
const warehouses = ref<Warehouse[]>([])

// 明细行
interface ItemRow {
  productId: number | undefined
  quantity: number
  warehouseId: number | undefined
  locationCode: string
  locations: Location[]
  locationLoading: boolean
}

const items = ref<ItemRow[]>([])

// 加载商品列表
const loadProducts = async () => {
  productLoading.value = true
  try {
    const res = await getProducts()
    products.value = res.data
  } catch (e: any) {
    ElMessage.error('加载商品列表失败')
  } finally {
    productLoading.value = false
  }
}

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const res = await getWarehouses()
    warehouses.value = res.data
  } catch (e: any) {
    ElMessage.error('加载仓库列表失败')
  }
}

onMounted(() => {
  loadProducts()
  loadWarehouses()
})

// 添加明细行
const addItem = () => {
  items.value.push({
    productId: undefined,
    quantity: 1,
    warehouseId: undefined,
    locationCode: '',
    locations: [],
    locationLoading: false,
  })
}

// 删除明细行
const removeItem = (index: number) => {
  items.value.splice(index, 1)
}

// 仓库变更时加载库位
const handleWarehouseChange = async (row: ItemRow) => {
  row.locationCode = ''
  row.locations = []
  if (!row.warehouseId) return

  row.locationLoading = true
  try {
    const res = await getLocations(row.warehouseId)
    row.locations = res.data
  } catch (e: any) {
    ElMessage.error('加载库位列表失败')
  } finally {
    row.locationLoading = false
  }
}

// 商品搜索过滤（下拉搜索）
const filteredProducts = computed(() => {
  return products.value
})

// 提交入库单
const handleSubmit = async () => {
  // 校验
  if (!supplierName.value.trim()) {
    ElMessage.warning('请输入供应商名称')
    return
  }
  if (items.value.length === 0) {
    ElMessage.warning('请至少添加一条入库明细')
    return
  }

  for (let i = 0; i < items.value.length; i++) {
    const row = items.value[i]
    if (!row.productId) {
      ElMessage.warning(`第 ${i + 1} 行请选择商品`)
      return
    }
    if (!row.locationCode) {
      ElMessage.warning(`第 ${i + 1} 行请选择库位`)
      return
    }
    if (!row.quantity || row.quantity < 1) {
      ElMessage.warning(`第 ${i + 1} 行数量必须大于0`)
      return
    }
  }

  submitting.value = true
  try {
    const data = {
      supplierName: supplierName.value,
      items: items.value.map((row) => ({
        productId: row.productId!,
        quantity: row.quantity,
        locationCode: row.locationCode,
      })),
    }
    const res = await createInboundOrder(data)
    ElMessage.success(`入库单创建成功，单号: ${res.data.orderNo}`)

    // 清空表单
    supplierName.value = ''
    items.value = []
  } catch (e: any) {
    const msg = e.response?.data?.message || '创建失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <h3>入库管理</h3>

    <el-form label-width="100px" style="max-width: 900px">
      <el-form-item label="供应商名称" required>
        <el-input v-model="supplierName" placeholder="请输入供应商名称" maxlength="200" />
      </el-form-item>

      <el-form-item label="入库明细">
        <el-button type="primary" @click="addItem">+ 添加明细</el-button>
      </el-form-item>
    </el-form>

    <!-- 明细列表 -->
    <el-table v-if="items.length > 0" :data="items" border style="width: 100%; margin-bottom: 16px">
      <el-table-column type="index" label="#" width="50" />

      <!-- 商品选择 -->
      <el-table-column label="商品" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.productId"
            filterable
            placeholder="请选择商品"
            style="width: 100%"
          >
            <el-option
              v-for="p in filteredProducts"
              :key="p.id"
              :label="`${p.name} (${p.sku})`"
              :value="p.id"
            />
          </el-select>
        </template>
      </el-table-column>

      <!-- 仓库选择 -->
      <el-table-column label="仓库" width="160">
        <template #default="{ row }">
          <el-select
            v-model="row.warehouseId"
            placeholder="选择仓库"
            style="width: 100%"
            @change="handleWarehouseChange(row)"
          >
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="w.name"
              :value="w.id"
            />
          </el-select>
        </template>
      </el-table-column>

      <!-- 库位选择 -->
      <el-table-column label="库位" width="160">
        <template #default="{ row }">
          <el-select
            v-model="row.locationCode"
            placeholder="选择库位"
            style="width: 100%"
            :loading="row.locationLoading"
            :disabled="!row.warehouseId"
          >
            <el-option
              v-for="loc in row.locations"
              :key="loc.code"
              :label="loc.code"
              :value="loc.code"
            />
          </el-select>
        </template>
      </el-table-column>

      <!-- 数量 -->
      <el-table-column label="数量" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.quantity" :min="1" :max="99999" size="small" style="width: 100%" />
        </template>
      </el-table-column>

      <!-- 操作 -->
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button type="danger" size="small" text @click="removeItem($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="items.length === 0" description="请点击【添加明细】按钮添加入库商品" />

    <!-- 提交按钮 -->
    <div style="margin-top: 16px">
      <el-button
        type="success"
        size="large"
        :loading="submitting"
        @click="handleSubmit"
        :disabled="items.length === 0"
      >
        提交入库单
      </el-button>
    </div>
  </div>
</template>

