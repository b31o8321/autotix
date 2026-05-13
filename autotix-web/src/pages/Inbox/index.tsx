import { useEffect, useState } from 'react';
import { Card, List, Tag, Button, Typography, Space, Badge } from 'antd';
import { history } from 'umi';
import { subscribeInbox, type InboxEvent } from '@/services/inbox';
import { getAccessToken } from '@/utils/auth';

const { Text } = Typography;

const MAX_EVENTS = 100;

const KIND_COLORS: Record<string, string> = {
  TICKET_CREATED: 'green',
  AI_REPLIED: 'blue',
  AGENT_REPLIED: 'purple',
  STATUS_CHANGED: 'orange',
  ASSIGNED: 'cyan',
  NEW_MESSAGE: 'default',
};

export default function InboxPage() {
  const [events, setEvents] = useState<InboxEvent[]>([]);

  useEffect(() => {
    const token = getAccessToken();
    if (!token) return;
    const unsubscribe = subscribeInbox(token, (event) => {
      setEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS));
    });
    return unsubscribe;
  }, []);

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Live Inbox</Typography.Title>
        <Badge count={events.length} showZero color="blue" />
      </div>
      <List<InboxEvent>
        dataSource={events}
        rowKey={(e) => `${e.kind}-${e.ticketId}-${e.occurredAt}`}
        renderItem={(event) => (
          <List.Item
            style={{
              background: event.kind === 'AI_REPLIED'
                ? '#e6f4ff'
                : event.kind === 'AGENT_REPLIED'
                ? '#f0f5ff'
                : undefined,
              borderRadius: 4,
              marginBottom: 4,
              padding: '8px 12px',
            }}
            actions={[
              <Button
                size="small"
                type="link"
                key="open"
                onClick={() => history.push(`/desk/${event.ticketId}`)}
              >
                Open ticket
              </Button>,
            ]}
          >
            <List.Item.Meta
              title={
                <Space size="small">
                  <Tag color={KIND_COLORS[event.kind] || 'default'}>{event.kind}</Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {new Date(event.occurredAt).toLocaleString()}
                  </Text>
                </Space>
              }
              description={
                <Space direction="vertical" size={0}>
                  <Text>{event.summary}</Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>Ticket: {event.ticketId}</Text>
                </Space>
              }
            />
          </List.Item>
        )}
        locale={{ emptyText: 'No events yet. Waiting for live updates...' }}
      />
    </Space>
  );
}
