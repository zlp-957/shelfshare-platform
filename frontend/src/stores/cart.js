import { defineStore } from 'pinia'

/**
 * 购物车
 * <p>
 * 只存「用户感兴趣的书」，不记录借阅还是购买——
 * 加购那一刻不该逼用户做决定，到购物车结算时再统一选。
 * 真正的方式由 Cart.vue 的模式切换决定，结算时才写进提交数据。
 */
export const useCartStore = defineStore('cart', {
  state: () => ({
    // [{ bookId, name, author, cover, price }]
    // 兼容旧数据：历史条目可能带 mode，这里丢掉，统一由结算时决定
    items: JSON.parse(localStorage.getItem('cartItems') || '[]').map((i) => ({
      bookId: i.bookId,
      name: i.name,
      author: i.author,
      cover: i.cover,
      price: i.price
    }))
  }),
  getters: {
    count: (state) => state.items.length
  },
  actions: {
    persist() {
      localStorage.setItem('cartItems', JSON.stringify(this.items))
    },
    has(bookId) {
      return this.items.some((i) => i.bookId === bookId)
    },
    add(item) {
      if (!this.has(item.bookId)) {
        this.items.push({
          bookId: item.bookId,
          name: item.name,
          author: item.author,
          cover: item.cover,
          price: item.price
        })
        this.persist()
      }
    },
    remove(bookId) {
      this.items = this.items.filter((i) => i.bookId !== bookId)
      this.persist()
    },
    /** 批量移除：结算成功后只清掉已结算的部分，未勾选的留在购物车 */
    removeMany(bookIds) {
      const set = new Set(bookIds)
      this.items = this.items.filter((i) => !set.has(i.bookId))
      this.persist()
    },
    clear() {
      this.items = []
      this.persist()
    }
  }
})
