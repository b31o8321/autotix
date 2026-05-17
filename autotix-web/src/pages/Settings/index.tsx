import { Card, Row, Col, Typography } from 'antd';
import { Link } from 'umi';
import { hasRole } from '@/utils/auth';

export default function SettingsIndex() {
  const isAdmin = hasRole('ADMIN');

  return (
    <div style={{ padding: 24 }}>
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
            <Card title="AI Configuration" hoverable>
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
            <Col span={8}>
              <Link to="/settings/tags">
                <Card title="Tags" hoverable>
                  Manage tag definitions, colors and categories
                </Card>
              </Link>
            </Col>
            <Col span={8}>
              <Link to="/settings/custom-fields">
                <Card title="Custom Fields" hoverable>
                  Manage custom field schema for tickets and customers
                </Card>
              </Link>
            </Col>
            <Col span={8}>
              <Link to="/settings/macros">
                <Card title="Macros" hoverable>
                  Manage saved-reply templates for agents
                </Card>
              </Link>
            </Col>
            <Col span={8}>
              <Link to="/settings/general">
                <Card title="General" hoverable>
                  Global AI auto-reply toggle and system settings
                </Card>
              </Link>
            </Col>
            <Col span={8}>
              <Link to="/settings/notifications">
                <Card title="Notifications" hoverable>
                  Email and Slack alerts for SLA breaches and other events
                </Card>
              </Link>
            </Col>
          </>
        )}
      </Row>
    </div>
  );
}
