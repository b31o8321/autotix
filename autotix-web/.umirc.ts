import { defineConfig } from 'umi';

export default defineConfig({
  title: 'Autotix',
  npmClient: 'pnpm',
  history: { type: 'browser' },
  routes: [
    { path: '/login', component: '@/pages/Login' },
    {
      path: '/',
      component: '@/layouts/AppLayout',
      routes: [
        { path: '/', redirect: '/desk' },
        { path: 'desk', component: '@/pages/Desk' },
        { path: 'desk/:ticketId', component: '@/pages/Desk/Detail' },
        { path: 'inbox', component: '@/pages/Inbox' },
        { path: 'reports', component: '@/pages/Reports' },
        { path: 'settings', component: '@/pages/Settings' },
        { path: 'settings/channels', component: '@/pages/Settings/Channels' },
        { path: 'settings/ai', component: '@/pages/Settings/AIConfig' },
        { path: 'settings/users', component: '@/pages/Settings/Users' },
        { path: 'settings/automation', component: '@/pages/Settings/Automation' },
      ],
    },
  ],
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/v2/webhook': { target: 'http://localhost:8080', changeOrigin: true },
  },
});
