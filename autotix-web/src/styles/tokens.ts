import type { ThemeConfig } from 'antd';

export const autotixTheme: ThemeConfig = {
  token: {
    colorPrimary: '#2962FF',
    colorSuccess: '#16A34A',
    colorWarning: '#F59E0B',
    colorError: '#DC2626',
    fontSize: 13,
    borderRadius: 6,
    colorBgLayout: '#F7F9FB',
    colorTextBase: '#0B1426',
    fontFamily: '-apple-system, "SF Pro Text", "PingFang SC", "Helvetica Neue", Arial, sans-serif',
  },
  components: {
    Card: { paddingLG: 16 },
    Table: { rowHoverBg: '#F7F9FB' },
    Button: { controlHeight: 28 },
    Tag: { defaultBg: '#EEF2F6' },
    Layout: { headerBg: '#FFFFFF', siderBg: '#0B1426' },
  },
};
