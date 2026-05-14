import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ReactNode } from 'react';

export function rootContainer(container: ReactNode) {
  return <ConfigProvider locale={zhCN}>{container}</ConfigProvider>;
}

// Request interceptor + auth/401 handling live in `src/utils/request.ts`.
// Base UmiJS (non-@umijs/max) does not register `request` as a runtime key,
// so do NOT export `request` from this file.
