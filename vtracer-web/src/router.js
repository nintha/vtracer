
const routers = [
    {
        path: '/',
        meta: {
            title: 'V-Tracer'
        },
        component: (resolve) => require(['./views/index.vue'], resolve)
    },
    {
        path: '/tracer/:subpage',
        meta: {
            title: 'V-Tracer'
        },
        component: (resolve) => require(['./views/tracer.vue'], resolve)
    },
];
export default routers;