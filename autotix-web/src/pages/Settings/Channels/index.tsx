import { useEffect, useState } from 'react';
import {
  Avatar, Card, Col, Input, Row, Space, Tag, Typography, message, Spin,
} from 'antd';
import { history } from 'umi';
import { getPlatforms, type PlatformDescriptorDTO } from '@/services/platform';
import { listChannels, type ChannelDTO } from '@/services/channel';

const { Title, Text } = Typography;
const { Search } = Input;

const CATEGORY_LABELS: Record<string, string> = {
  ticket: 'Ticket',
  chat: 'Chat',
  ecommerce: 'E-commerce',
  email: 'Email',
  test: 'Test',
  other: 'Other',
};

const CATEGORY_ORDER = ['ticket', 'chat', 'ecommerce', 'email', 'test', 'other'];

export default function ChannelsLandingPage() {
  const [platforms, setPlatforms] = useState<PlatformDescriptorDTO[]>([]);
  const [channels, setChannels] = useState<ChannelDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');

  useEffect(() => {
    setLoading(true);
    Promise.all([getPlatforms(), listChannels()])
      .then(([ps, cs]) => {
        setPlatforms(ps);
        setChannels(cs);
      })
      .catch(() => message.error('Failed to load platforms'))
      .finally(() => setLoading(false));
  }, []);

  const countByPlatform = channels.reduce<Record<string, number>>((acc, ch) => {
    acc[ch.platform] = (acc[ch.platform] ?? 0) + 1;
    return acc;
  }, {});

  const filtered = search
    ? platforms.filter((p) =>
        p.displayName.toLowerCase().includes(search.toLowerCase()) ||
        p.platform.toLowerCase().includes(search.toLowerCase())
      )
    : platforms;

  // Group by category
  const grouped = CATEGORY_ORDER.reduce<Record<string, PlatformDescriptorDTO[]>>((acc, cat) => {
    const items = filtered.filter((p) => p.category === cat);
    if (items.length > 0) acc[cat] = items;
    return acc;
  }, {});
  // Any categories not in CATEGORY_ORDER
  filtered.forEach((p) => {
    if (!CATEGORY_ORDER.includes(p.category)) {
      if (!grouped['other']) grouped['other'] = [];
      if (!grouped['other'].includes(p)) grouped['other'].push(p);
    }
  });

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={4} style={{ margin: 0 }}>Channels</Title>
        <Search
          placeholder="Search platforms..."
          allowClear
          style={{ width: 240 }}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>
      ) : (
        CATEGORY_ORDER.filter((cat) => grouped[cat]).map((cat) => (
          <div key={cat}>
            <Title level={5} style={{ marginBottom: 12, color: '#5A6B7D' }}>
              {CATEGORY_LABELS[cat] ?? cat}
            </Title>
            <Row gutter={[16, 16]}>
              {grouped[cat].map((p) => (
                <Col key={p.platform} xs={24} sm={12} md={8} lg={6}>
                  <PlatformCard
                    platform={p}
                    count={countByPlatform[p.platform] ?? 0}
                    onClick={() => history.push(`/settings/channels/${p.platform}`)}
                  />
                </Col>
              ))}
            </Row>
          </div>
        ))
      )}
    </Space>
  );
}

interface PlatformCardProps {
  platform: PlatformDescriptorDTO;
  count: number;
  onClick: () => void;
}

function PlatformCard({ platform, count, onClick }: PlatformCardProps) {
  const letter = platform.displayName.charAt(0).toUpperCase();

  return (
    <Card
      hoverable
      onClick={onClick}
      style={{ cursor: 'pointer', borderRadius: 8 }}
      styles={{ body: { padding: '16px' } }}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={8}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Avatar
            size={40}
            style={{
              background: platform.functional ? '#2962FF' : '#B0BEC5',
              fontWeight: 700,
              fontSize: 16,
              flexShrink: 0,
            }}
          >
            {letter}
          </Avatar>
          <div style={{ minWidth: 0 }}>
            <Text strong style={{ display: 'block', fontSize: 14 }}>
              {platform.displayName}
            </Text>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {count} channel{count !== 1 ? 's' : ''} connected
            </Text>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {platform.functional ? (
            <Tag color="green" style={{ margin: 0 }}>Functional</Tag>
          ) : (
            <Tag color="default" style={{ margin: 0 }}>Stub</Tag>
          )}
          <Tag color="blue" style={{ margin: 0 }}>{platform.authMethod}</Tag>
        </div>
      </Space>
    </Card>
  );
}
