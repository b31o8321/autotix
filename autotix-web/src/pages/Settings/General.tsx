import { Card, Typography } from 'antd';

export default function GeneralPage() {
  return (
    <div style={{ padding: 24 }}>
      <Typography.Title level={4}>General</Typography.Title>
      <Card>
        <Typography.Text type="secondary">Coming in slice 16 — global AI auto-reply toggle, reopen window days, and admin bootstrap info.</Typography.Text>
      </Card>
    </div>
  );
}
