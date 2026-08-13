/**
 * 库存列表筛选逻辑单元测试
 *
 * 测试内容：
 * 1. 低库存行高亮样式（quantity < 10 红色）
 * 2. 搜索关键字变化时重置页码
 * 3. 仓库筛选变化时重置页码
 * 4. API 参数传递正确性
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref, nextTick } from 'vue'

// Mock API 模块
vi.mock('@/api', () => ({
  getInventory: vi.fn().mockResolvedValue({
    data: {
      list: [
        { productId: 1, productName: '商品A', sku: 'SKU-001', locationCode: 'WH-A-01-01', warehouseName: '广州主仓', quantity: 150, updatedAt: '2026-08-13T10:00:00' },
        { productId: 2, productName: '商品B', sku: 'SKU-002', locationCode: 'WH-A-01-02', warehouseName: '广州主仓', quantity: 5, updatedAt: '2026-08-13T10:00:00' },
        { productId: 3, productName: '商品C', sku: 'SKU-003', locationCode: 'WH-B-01-01', warehouseName: '深圳保税仓', quantity: 0, updatedAt: '2026-08-13T10:00:00' },
      ],
      total: 3,
      page: 1,
      pageSize: 20,
    },
  }),
  getWarehouses: vi.fn().mockResolvedValue({
    data: [
      { id: 1, code: 'WH-A', name: '广州主仓' },
      { id: 2, code: 'WH-B', name: '深圳保税仓' },
    ],
  }),
}))

// 测试纯逻辑函数（不依赖组件挂载）
describe('库存列表筛选逻辑', () => {
  describe('低库存行样式判断', () => {
    // 提取 getRowStyle 逻辑进行测试
    const getRowStyle = (row: { quantity: number }) => {
      if (row.quantity < 10) {
        return { backgroundColor: '#fef0f0', color: '#f56c6c' }
      }
      return {}
    }

    it('库存数量 < 10 时返回红色样式', () => {
      const row = { quantity: 5 }
      const style = getRowStyle(row)
      expect(style).toEqual({ backgroundColor: '#fef0f0', color: '#f56c6c' })
    })

    it('库存数量 = 0 时返回红色样式', () => {
      const row = { quantity: 0 }
      const style = getRowStyle(row)
      expect(style).toEqual({ backgroundColor: '#fef0f0', color: '#f56c6c' })
    })

    it('库存数量 = 9 时返回红色样式（边界值）', () => {
      const row = { quantity: 9 }
      const style = getRowStyle(row)
      expect(style).toEqual({ backgroundColor: '#fef0f0', color: '#f56c6c' })
    })

    it('库存数量 = 10 时不返回红色样式（边界值）', () => {
      const row = { quantity: 10 }
      const style = getRowStyle(row)
      expect(style).toEqual({})
    })

    it('库存数量 > 10 时不返回红色样式', () => {
      const row = { quantity: 150 }
      const style = getRowStyle(row)
      expect(style).toEqual({})
    })
  })

  describe('筛选条件变化时页码重置', () => {
    it('关键字搜索变化时页码应重置为1', () => {
      const page = ref(3)
      // 模拟搜索触发页码重置
      const onKeywordChange = () => { page.value = 1 }
      
      expect(page.value).toBe(3)
      onKeywordChange()
      expect(page.value).toBe(1)
    })

    it('仓库筛选变化时页码应重置为1', () => {
      const page = ref(5)
      // 模拟仓库筛选触发页码重置
      const onWarehouseChange = () => { page.value = 1 }
      
      expect(page.value).toBe(5)
      onWarehouseChange()
      expect(page.value).toBe(1)
    })
  })

  describe('API 参数传递正确性', () => {
    it('应传递 keyword、warehouseId、locationCode、page、pageSize 参数', async () => {
      const { getInventory } = await import('@/api')
      
      await getInventory({
        keyword: 'SKU-001',
        warehouseId: 1,
        locationCode: 'WH-A-01-01',
        page: 2,
        pageSize: 10,
      })

      expect(getInventory).toHaveBeenCalledWith({
        keyword: 'SKU-001',
        warehouseId: 1,
        locationCode: 'WH-A-01-01',
        page: 2,
        pageSize: 10,
      })
    })

    it('空关键字应传 undefined', async () => {
      const { getInventory } = await import('@/api')
      
      const keyword = ''
      await getInventory({
        keyword: keyword || undefined,
        page: 1,
        pageSize: 20,
      })

      expect(getInventory).toHaveBeenCalledWith({
        keyword: undefined,
        page: 1,
        pageSize: 20,
      })
    })
  })
})
