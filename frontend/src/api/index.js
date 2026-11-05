import request from '../utils/request'

// ============ 认证 ============
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)

// ============ 密码找回 ============
export const applyPasswordReset = (data) => request.post('/password-reset/apply', data)
export const resetPasswordByCode = (data) => request.post('/password-reset/reset', data)
export const pagePasswordResets = (params) => request.get('/password-reset/page', { params })
export const countPasswordResets = () => request.get('/password-reset/pendingCount')
export const generateResetCode = (id) => request.post(`/password-reset/${id}/generate`)
export const rejectPasswordReset = (id, data) => request.post(`/password-reset/${id}/reject`, data)

// ============ 用户 ============
export const getUserInfo = () => request.get('/user/info')
export const updateProfile = (data) => request.post('/user/profile', data)
export const updatePassword = (data) => request.post('/user/password', data)
export const uploadAvatar = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/avatar', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const updatePhone = (data) => request.post('/user/phone', data)
export const pageReaders = (params) => request.get('/user/readers', { params })
export const addReader = (data) => request.post('/user/reader', data)
export const updateReader = (id, data) => request.put(`/user/reader/${id}`, data)
export const toggleReader = (id) => request.post(`/user/reader/${id}/toggle`)

// ============ 分类 ============
export const listBookTypes = () => request.get('/bookType/list')
export const addBookType = (data) => request.post('/bookType/add', data)
export const updateBookType = (data) => request.put('/bookType/update', data)
export const deleteBookType = (id) => request.delete(`/bookType/delete/${id}`)

// ============ 图书 ============
export const pageBooks = (params) => request.get('/book/page', { params })
export const getBookDetail = (id) => request.get(`/book/${id}`)
export const addBook = (data) => request.post('/book/add', data)
export const updateBook = (data) => request.put('/book/update', data)
export const deleteBook = (id) => request.delete(`/book/delete/${id}`)

// ============ 取书码 ============
// 一个用户一个码，覆盖全部待取（借阅 + 购买），30 分钟有效、核销后作废
export const generatePickupCode = () => request.post('/pickup/code')
export const getMyPickupCode = () => request.get('/pickup/code')
export const getPickupPending = (keyword) => request.get('/pickup/pending', { params: { keyword } })
export const doPickup = (data) => request.post('/pickup/do', data)

// ============ 借阅 ============
export const applyBorrow = (bookId) => request.post('/borrow/apply', { bookId })
export const cancelBorrow = (borrowId) => request.post(`/borrow/cancel/${borrowId}`)
export const checkOut = (data) => request.post('/borrow/checkOut', data)
export const checkIn = (data) => request.post('/borrow/checkIn', data)
export const myBorrows = (status) => request.get('/borrow/my', { params: { status } })
export const pageBorrows = (params) => request.get('/borrow/page', { params })
export const processOverdue = () => request.post('/borrow/processOverdue')

// ============ 副本 ============
export const addBookItems = (data) => request.post('/bookItem/add', data)
export const listBookItems = (bookId) => request.get(`/bookItem/list/${bookId}`)
export const pageBookItems = (params) => request.get('/bookItem/page', { params })
export const updateBookItemStatus = (id, data) => request.post(`/bookItem/${id}/status`, data)
export const getBookItemByBarcode = (barcode) => request.get(`/bookItem/barcode/${barcode}`)

// ============ 支付 ============
export const getPayForm = (orderNo) => request.get(`/pay/form/${orderNo}`)
export const getPayStatus = (orderNo) => request.get(`/pay/status/${orderNo}`)
// 模拟支付（后端 mock-enabled 开启时可用，用于本地联调与自动化测试）
export const mockPay = (orderNo) => request.post(`/pay/mock/pay/${orderNo}`)

// ============ 规则确认 ============
export const getAgreement = (type) => request.get('/agreement/current', { params: { type } })
export const agreeAgreement = (type) => request.post('/agreement/agree', { type })
export const myAgreements = () => request.get('/agreement/mine')

// ============ 预约 ============
export const reserveBook = (bookId) => request.post('/reserve/add', { bookId })
export const cancelReserve = (id) => request.post(`/reserve/cancel/${id}`)
export const myReserves = () => request.get('/reserve/my')
export const pageReserves = (params) => request.get('/reserve/page', { params })
export const processExpired = () => request.post('/reserve/processExpired')

// ============ 购买 ============
export const buyBook = (bookId) => request.post(`/purchase/buy/${bookId}`)
export const myOrders = () => request.get('/purchase/my')
export const pageOrders = (params) => request.get('/purchase/page', { params })
// 待取书阶段取消订单（全额退书款）
export const cancelPurchaseOrder = (orderId) => request.post(`/purchase/cancel/${orderId}`)
// 管理员扫码核销取书（待取书 -> 已取书）
export const pickupPurchase = (barcode) => request.post('/purchase/pickup', { barcode })

// ============ 购物车 ============
export const cartCheckout = (items) => request.post('/cart/checkout', { items })

// ============ 公告 ============
export const pageNotices = (params) => request.get('/notice/page', { params })
export const latestNotices = () => request.get('/notice/latest')
export const addNotice = (data) => request.post('/notice/add', data)
export const updateNotice = (data) => request.put('/notice/update', data)
export const deleteNotice = (id) => request.delete(`/notice/delete/${id}`)

// ============ 统计 ============
export const getStat = () => request.get('/stat/overview')

// ============ 站内信 ============
export const myMessages = (params) => request.get('/message/my', { params })
export const unreadMessageCount = () => request.get('/message/unread-count')
export const readMessage = (id) => request.post(`/message/${id}/read`)
export const readAllMessages = () => request.post('/message/read-all')

// ============ 资金对账 ============
export const runReconcile = (data) => request.post('/reconcile/run', data || {})
export const pageReconcile = (params) => request.get('/reconcile/page', { params })
export const handleReconcile = (id, data) => request.post(`/reconcile/${id}/handle`, data)

// ============ 操作审计 ============
export const pageOperationLogs = (params) => request.get('/operation-log/page', { params })

// ============ ISBN 自动录入 ============
export const lookupIsbn = (isbn) => request.get(`/book/isbn/${encodeURIComponent(isbn)}`)
