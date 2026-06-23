import request from './request'

export const follow = (id, isFollow) => request.put(`/follow/${id}/${isFollow}`)
export const isFollow = (id) => request.get(`/follow/or/not/${id}`)
export const commonFollow = (id) => request.get(`/follow/common/${id}`)
export const followList = (id) => request.get(`/follow/list/${id}`)
export const fansList = (id) => request.get(`/follow/fans/${id}`)
