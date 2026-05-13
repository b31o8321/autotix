/**
 * HTTP request wrapper.
 *
 * In the UmiJS runtime, `app.tsx` exports a `request` config that sets
 * up interceptors at the framework level. For tests and direct imports,
 * this module provides a standalone umi-request instance with the auth
 * header injected via an interceptor.
 *
 * All services import `request` from here (or from 'umi' — both work at
 * runtime because umi re-exports this via @umijs/max; this file is the
 * fallback for typecheck + tests).
 */
import umiRequest, { extend } from 'umi-request';
import { getAccessToken, clearTokens } from '@/utils/auth';

const request = extend({
  timeout: 30_000,
  errorHandler(error: { response?: Response; data?: { message?: string } }) {
    if (error?.response?.status === 401) {
      clearTokens();
      window.location.href = '/login';
    }
    throw error;
  },
});

// Inject auth header on every request
request.interceptors.request.use((url, options) => {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> | undefined),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return { url, options: { ...options, headers } };
});

export default request;
export { request };
