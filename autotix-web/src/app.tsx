// TODO: Umi runtime config — global layout, antd ConfigProvider, error boundary, request interceptor.
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ReactNode } from 'react';

export function rootContainer(container: ReactNode) {
  // TODO: theme tokens, default locale (i18n switch later)
  return <ConfigProvider locale={zhCN}>{container}</ConfigProvider>;
}

// TODO: export request config for umi-request (auth header, error handling)
export const request = {
  timeout: 30000,
  // errorConfig: { ... }
};
