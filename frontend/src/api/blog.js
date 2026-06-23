import request from './request'

export const saveBlog = (data) => request.post('/blog', data)
export const likeBlog = (id) => request.put(`/blog/like/${id}`)
export const getBlogLikes = (id, top = 5) => request.get(`/blog/likes/${id}`, { params: { top } })
export const getHotBlogs = (current = 1) => request.get('/blog/hot', { params: { current } })
export const getMyBlogs = (current = 1) => request.get('/blog/of/me', { params: { current } })
export const getFeed = (max, offset = 0) => request.get('/blog/of/follow', { params: { max, offset } })
export const getBlogById = (id) => request.get(`/blog/${id}`)
