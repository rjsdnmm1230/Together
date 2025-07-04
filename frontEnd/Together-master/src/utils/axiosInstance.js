// src/utils/axiosInstance.js
import axios from 'axios'

const instance = axios.create({
    baseURL: 'http://localhost:8081', // 필요에 따라 수정
    // baseURL: 'http://25.12.59.4:3000', // 필요에 따라 수정
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
    },
})
// 모든 요청에 로컬 스토리지의 인증 헤더를 자동 포함한다
instance.interceptors.request.use(config => {
    const token = localStorage.getItem('authHeader')
    if (token) {
        config.headers.Authorization = token
    } else {
        delete config.headers.Authorization
    }
    return config
})

export default instance
