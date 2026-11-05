import { mockPay } from '../api'

/**
 * 支付跳转工具
 *
 * 后端返回的是支付宝收银台表单 HTML（一段 <form> + script），
 * 把它插入页面后浏览器会自动提交跳转到支付宝。
 *
 * 当后端开启 mock-enabled 时，payForm 为空字符串，
 * 此时直接调模拟支付接口并跳到结果页。
 *
 * @param {string} html     后端返回的表单 HTML
 * @param {string} orderNo  支付单号（mock 模式必传）
 */
export async function submitPayForm(html, orderNo) {
  if (!html) {
    if (!orderNo) {
      throw new Error('支付表单为空')
    }
    await mockPay(orderNo)
    window.location.href = `/pay/result?out_trade_no=${encodeURIComponent(orderNo)}`
    return
  }

  const holder = document.createElement('div')
  holder.style.display = 'none'
  holder.innerHTML = html
  document.body.appendChild(holder)

  const form = holder.querySelector('form')
  if (form) {
    form.submit()
    return
  }
  // 个别情况下返回的是 script 片段，执行一次即可触发跳转
  holder.querySelectorAll('script').forEach((old) => {
    const s = document.createElement('script')
    s.textContent = old.textContent
    document.body.appendChild(s)
  })
  setTimeout(() => holder.remove(), 3000)
}

/**
 * 轮询支付状态，直到支付完成或超时
 *
 * @param {Function} fetcher  () => Promise<number> 返回支付单状态
 * @param {Object}   options  { interval, timeout }
 * @returns {Promise<number>} 最终状态，2 表示超时未支付
 */
export function pollPayStatus(fetcher, { interval = 2000, timeout = 15 * 60 * 1000 } = {}) {
  return new Promise((resolve) => {
    const start = Date.now()
    const timer = setInterval(async () => {
      try {
        const status = await fetcher()
        if (status === 1) {
          clearInterval(timer)
          resolve(1)
          return
        }
        if (status === 2 || status === 3) {
          clearInterval(timer)
          resolve(status)
          return
        }
      } catch (e) {
        // 网络波动忽略，继续轮询
      }
      if (Date.now() - start > timeout) {
        clearInterval(timer)
        resolve(2)
      }
    }, interval)
  })
}
