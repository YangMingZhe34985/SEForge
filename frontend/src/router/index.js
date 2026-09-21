import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import Login from '../views/Login.vue'
import Function from '../views/Function.vue';

const routes = [
    {
        path: '/',
        name: 'home',
        component:Home
    },
    {
        path: '/login',
        name: 'Login',
        component:Login
    },
    {
        path: '/function',//path: '/function/userId',
        name: 'Function',
        component: () => import('@/views/Function.vue'),
        props: true // 将路由参数作为 props 传递给组件
    },
]

const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior(to) {
        if (to.hash) {
            return {
                el: to.hash,
                behavior: 'smooth'
            }
        }
        return { top: 0 }
    }
})
// 在router/index.js中
router.beforeEach((to, from, next) => {
    const isLoggedIn = localStorage.getItem('userId') !== null;

    if (to.path === '/Function' && !isLoggedIn) {
        // 如果尝试访问功能页但未登录，重定向到Home页
        next('/Home');
    } else if (to.path === '/Login' && isLoggedIn) {
        // 如果已登录但尝试访问登录页，重定向到功能页
        next('/Function');
    } else {
        // 否则正常导航
        next();
    }
});

export default router