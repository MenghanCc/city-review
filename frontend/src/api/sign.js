import request from './request'

export const sign = () => request.post('/sign')
export const signCount = () => request.get('/sign/count')
export const signCalendar = () => request.get('/sign/calendar')
