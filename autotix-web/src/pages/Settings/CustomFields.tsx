import { Card, Typography } from 'antd';

export default function CustomFieldsPage() {
  return (
    <div style={{ padding: 24 }}>
      <Typography.Title level={4}>Custom Fields</Typography.Title>
      <Card>
        <Typography.Text type="secondary">Coming in slice 16 — manage custom field schema (name, type, appliesTo).</Typography.Text>
      </Card>
    </div>
  );
}
