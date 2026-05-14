import { Card, Row, Col, Typography } from 'antd';
import { Link } from 'umi';
import { hasRole } from '@/utils/auth';

export default function SettingsIndex() {
  const isAdmin = hasRole('ADMIN');

  return (
    <div>
      <Typography.Title level={4}>Settings</Typography.Title>
      <Row gutter={[16, 16]}>
        <Col span={8}>
          <Link to="/settings/channels">
            <Card title="Channels" hoverable>
              Manage connected support channels
            </Card>
          </Link>
        </Col>
        <Col span={8}>
          <Link to="/settings/ai">
            <Card title="AI Config" hoverable>
              Configure AI endpoint, model and prompt
            </Card>
          </Link>
        </Col>
        {isAdmin && (
          <>
            <Col span={8}>
              <Link to="/settings/automation">
                <Card title="Automation" hoverable>
                  Manage automation rules and triggers
                </Card>
              </Link>
            </Col>
            <Col span={8}>
              <Link to="/settings/users">
                <Card title="Users" hoverable>
                  Manage agents and administrators
                </Card>
              </Link>
            </Col>
            <Col span={8}>
              <Link to="/settings/sla">
                <Card title="SLA Policies" hoverable>
                  Configure SLA targets per ticket priority
                </Card>
              </Link>
            </Col>
          </>
        )}
      </Row>
    </div>
  );
}
