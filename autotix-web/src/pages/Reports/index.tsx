import { Card, Col, Row, Statistic, Typography } from 'antd';

// TODO: Backend does not yet have a /api/reports endpoint (planned for Slice 7).
// These statistics are placeholder zeros until that endpoint is implemented.

export default function ReportsPage() {
  return (
    <div>
      <Typography.Title level={4}>Reports</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col span={8}>
          <Card>
            <Statistic
              title="Open Tickets"
              value={0}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Tickets Today"
              value={0}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="AI Auto-Resolved (24h)"
              value={0}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
