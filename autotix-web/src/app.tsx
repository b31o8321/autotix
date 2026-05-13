import { ConfigProvider, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { ReactNode } from 'react';
import { history } from 'umi';
import { clearTokens, getAccessToken } from '@/utils/auth';

export function rootContainer(container: ReactNode) {
  return <ConfigProvider locale={zhCN}>{container}</ConfigProvider>;
}

export const request = {
  timeout: 30000,
  requestInterceptors: [
    (config: { url?: string; headers?: Record<string, string>; [key: string]: unknown }) => {
      const token = getAccessToken();
      if (token) {
        config.headers = {
          ...config.headers,
          Authorization: `Bearer ${token}`,
        };
      }
      return config;
    },
  ],
  responseInterceptors: [
    (response: Response) => {
      return response;
    },
  ],
  errorConfig: {
    errorHandler(
      error: { response?: { status?: number; data?: { message?: string } }; message?: string },
    ) {
      const status = error?.response?.status;
      if (status === 401) {
        clearTokens();
        history.push('/login');
        return;
      }
      const msg = error?.response?.data?.message || error?.message || 'Request failed';
      message.error(msg);
    },
    errorThrower(res: { success?: boolean; errorMessage?: string }) {
      if (!res?.success) {
        const err: Error & { info?: typeof res } = new Error(res?.errorMessage || 'Error');
        err.info = res;
        throw err;
      }
    },
  },
};
