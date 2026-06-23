import request from './request'

export const getVoucherList = (shopId) => request.get(`/voucher/list/${shopId}`)
export const seckillVoucher = (voucherId) => request.post(`/voucher-order/seckill/${voucherId}`)
export const querySeckillResult = (voucherId) => request.get(`/voucher-order/result/${voucherId}`)
