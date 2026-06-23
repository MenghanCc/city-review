import request from './request'

export const getShopById = (id) => request.get(`/shop/${id}`)
export const getShopByType = (typeId, current = 1) => request.get('/shop/of/type', { params: { typeId, current } })
export const searchShop = (name, current = 1) => request.get('/shop/of/name', { params: { name, current } })
export const getNearbyShops = (x, y, radius = 5000, current = 1) => request.get('/shop/of/nearby', { params: { x, y, radius, current } })
export const getShopUV = (id) => request.get(`/shop/uv/${id}`)
export const getShopTypeList = () => request.get('/shop-type/list')
