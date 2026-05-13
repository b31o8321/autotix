import { defineConfig } from 'umi';

// TODO: finalize routing, proxy to autotix-server, theme
export default defineConfig({
  title: 'Autotix',
  npmClient: 'pnpm',
  history: { type: 'browser' },
  routes: [
    { path: '/', redirect: '/desk' },
    { path: '/login', component: '@/pages/Login' },
    { path: '/desk', component: '@/pages/Desk' },
    { path: '/desk/:ticketId', component: '@/pages/Desk/Detail' },
    { path: '/inbox', component: '@/pages/Inbox' },
    { path: '/settings', component: '@/pages/Settings' },
    { path: '/settings/channels', component: '@/pages/Settings/Channels' },
    { path: '/settings/ai', component: '@/pages/Settings/AIConfig' },
    { path: '/settings/users', component: '@/pages/Settings/Users' },
    { path: '/reports', component: '@/pages/Reports' },
  ],
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/v2/webhook': { target: 'http://localhost:8080', changeOrigin: true },
  },
});
