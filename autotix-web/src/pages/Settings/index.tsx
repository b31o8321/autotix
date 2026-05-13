// TODO: Settings root — index page with nav cards (Channels / AI / Automation).
import { Card, Row, Col } from 'antd';
import { Link } from 'umi';

export default function SettingsIndex() {
  // TODO: route cards with description; potentially merge Channels/AI/Automation into one sider layout
  return (
    <Row gutter={16}>
      <Col span={8}><Link to="/settings/channels"><Card title="Channels">{/* TODO */}</Card></Link></Col>
      <Col span={8}><Link to="/settings/ai"><Card title="AI Config">{/* TODO */}</Card></Link></Col>
      <Col span={8}><Card title="Automation">{/* TODO */}</Card></Col>
    </Row>
  );
}
