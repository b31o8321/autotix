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
        { path: '/', redirect: '/inbox' },
        { path: 'inbox', component: '@/pages/Inbox' },
        { path: 'reports', component: '@/pages/Reports' },
        { path: 'settings', component: '@/pages/Settings' },
        { path: 'settings/channels', component: '@/pages/Settings/Channels/index' },
        { path: 'settings/channels/:platform', component: '@/pages/Settings/Channels/PlatformChannels' },
        { path: 'settings/channels/:platform/new', component: '@/pages/Settings/Channels/PlatformAdd' },
        { path: 'settings/ai', component: '@/pages/Settings/AIConfig' },
        { path: 'settings/users', component: '@/pages/Settings/Users' },
        { path: 'settings/automation', component: '@/pages/Settings/Automation' },
        { path: 'settings/sla', component: '@/pages/Settings/SLA' },
        { path: 'settings/tags', component: '@/pages/Settings/Tags' },
        { path: 'settings/custom-fields', component: '@/pages/Settings/CustomFields' },
        { path: 'settings/general', component: '@/pages/Settings/General' },
      ],
    },
  ],
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/v2/webhook': { target: 'http://localhost:8080', changeOrigin: true },
  },
});
