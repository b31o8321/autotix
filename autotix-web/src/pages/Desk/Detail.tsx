import { useEffect, useState } from 'react';
import {
  Card, Tag, Space, Descriptions, Button, Input, message as antMessage,
  Spin, Divider, Typography, Dropdown, Menu, Switch, Select, Tabs, Timeline,
} from 'antd';
import { DownOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { useParams, history } from 'umi';
import {
  getTicket, replyTicket, solveTicket, closeTicket, assignTicket,
  changeTicketPriority, changeTicketType, listTicketActivity,
  type TicketDTO, type MessageDTO, type TicketActivity,
  type TicketPriority, type TicketType,
} from '@/services/ticket';
import { getCurrentUser } from '@/utils/auth';

const { TextArea } = Input;
const { Text } = Typography;

const STATUS_COLORS: Record<string, string> = {
  NEW: 'cyan',
  OPEN: 'blue',
  WAITING_ON_CUSTOMER: 'orange',
  WAITING_ON_INTERNAL: 'gold',
  SOLVED: 'green',
  CLOSED: 'default',
  SPAM: 'red',
};

const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'default',
  NORMAL: 'blue',
  HIGH: 'orange',
  URGENT: 'red',
};

const TERMINAL_STATUSES = ['CLOSED', 'SPAM'];

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const secs = Math.floor(diff / 1000);
  if (secs < 60) return `${secs}s ago`;
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return new Date(iso).toLocaleDateString();
}

function actionIcon(action: string): string {
  const icons: Record<string, string> = {
    CREATED: '🎫',
    REOPENED: '🔄',
    STATUS_CHANGED: '📋',
    ASSIGNED: '👤',
    UNASSIGNED: '➖',
    PRIORITY_CHANGED: '⚡',
    TYPE_CHANGED: '🏷',
    REPLIED_PUBLIC: '💬',
    REPLIED_INTERNAL: '🔒',
    SOLVED: '✅',
    PERMANENTLY_CLOSED: '🔐',
    MARKED_SPAM: '🚫',
    SPAWNED: '🌿',
    TAGS_CHANGED: '🏷',
  };
  return icons[action] || '•';
}

export default function TicketDetail() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const [ticket, setTicket] = useState<TicketDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [replyContent, setReplyContent] = useState('');
  const [solveAfter, setSolveAfter] = useState(false);
  const [replying, setReplying] = useState(false);
  const [isInternal, setIsInternal] = useState(false);
  const [activities, setActivities] = useState<TicketActivity[]>([]);

  const currentUser = getCurrentUser();
  const isAdmin = currentUser?.role === 'ADMIN';

  async function fetchTicket() {
    if (!ticketId) return;
    setLoading(true);
    try {
      const data = await getTicket(ticketId);
      setTicket(data);
    } finally {
      setLoading(false);
    }
  }

  async function fetchActivity() {
    if (!ticketId) return;
    try {
      const data = await listTicketActivity(ticketId);
      setActivities(data);
    } catch {
      // ignore
    }
  }

  useEffect(() => {
    fetchTicket();
    fetchActivity();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId]);

  async function handleReply() {
    if (!ticketId || !replyContent.trim()) return;
    setReplying(true);
    try {
      await replyTicket(ticketId, replyContent, solveAfter, isInternal);
      setReplyContent('');
      setIsInternal(false);
      antMessage.success(isInternal ? 'Internal note saved' : solveAfter ? 'Replied and solved' : 'Replied');
      await fetchTicket();
      await fetchActivity();
    } catch {
      antMessage.error('Reply failed');
    } finally {
      setReplying(false);
    }
  }

  async function handleSolve() {
    if (!ticketId) return;
    try {
      await solveTicket(ticketId);
      antMessage.success('Ticket solved');
      await fetchTicket();
      await fetchActivity();
    } catch {
      antMessage.error('Solve failed');
    }
  }

  async function handlePermanentClose() {
    if (!ticketId) return;
    try {
      await closeTicket(ticketId);
      antMessage.success('Ticket permanently closed');
      await fetchTicket();
      await fetchActivity();
    } catch {
      antMessage.error('Close failed');
    }
  }

  async function handleAssignToMe() {
    if (!ticketId || !currentUser) return;
    try {
      await assignTicket(ticketId, currentUser.id);
      antMessage.success('Assigned to you');
      await fetchTicket();
      await fetchActivity();
    } catch {
      antMessage.error('Assign failed');
    }
  }

  async function handlePriorityChange(value: TicketPriority) {
    if (!ticketId) return;
    try {
      await changeTicketPriority(ticketId, value);
      antMessage.success('Priority updated');
      await fetchTicket();
      await fetchActivity();
    } catch {
      antMessage.error('Failed to update priority');
    }
  }

  async function handleTypeChange(value: TicketType) {
    if (!ticketId) return;
    try {
      await changeTicketType(ticketId, value);
      antMessage.success('Type updated');
      await fetchTicket();
      await fetchActivity();
    } catch {
      antMessage.error('Failed to update type');
    }
  }

  if (loading) return <Spin size="large" />;
  if (!ticket) return <Text type="secondary">Ticket not found</Text>;

  const isTerminal = TERMINAL_STATUSES.includes(ticket.status);
  const isSolved = ticket.status === 'SOLVED';

  const adminMenu = (
    <Menu
      items={[
        {
          key: 'permanent-close',
          label: 'Permanently Close',
          danger: true,
          onClick: handlePermanentClose,
        },
      ]}
    />
  );

  const messagesTab = (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      {(ticket.messages || []).map((msg: MessageDTO, idx: number) => {
        const isOutbound = msg.direction === 'OUTBOUND';
        const isInternalMsg = msg.visibility === 'INTERNAL';
        const isAI = msg.author === 'ai';
        return (
          <div
            key={idx}
            style={{
              display: 'flex',
              justifyContent: isOutbound ? 'flex-end' : 'flex-start',
            }}
          >
            <Card
              size="small"
              style={{
                maxWidth: '70%',
                background: isInternalMsg
                  ? '#fffbe6'
                  : isOutbound
                  ? isAI
                    ? '#e6f4ff'
                    : '#f0f5ff'
                  : '#fafafa',
                border: isInternalMsg
                  ? '1px solid #faad14'
                  : isAI
                  ? '1px solid #91caff'
                  : undefined,
              }}
              bodyStyle={{ padding: '8px 12px' }}
            >
              <Space size="small">
                {isInternalMsg && <Tag color="gold" style={{ fontSize: 10 }}>Internal Note</Tag>}
                {isAI && !isInternalMsg && <Tag color="blue" style={{ fontSize: 10 }}>AI</Tag>}
                <Text type="secondary" style={{ fontSize: 11 }}>{msg.author}</Text>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {new Date(msg.occurredAt).toLocaleString()}
                </Text>
              </Space>
              <div style={{ marginTop: 4, whiteSpace: 'pre-wrap' }}>{msg.content}</div>
            </Card>
          </div>
        );
      })}
    </Space>
  );

  const historyTab = (
    <Timeline>
      {activities.map((a) => (
        <Timeline.Item key={a.id} dot={<span>{actionIcon(a.action)}</span>}>
          <Space direction="vertical" size={0}>
            <Text>
              <b>{a.actor}</b> — {a.action.replace(/_/g, ' ')}
            </Text>
            {a.details && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                {a.details}
              </Text>
            )}
            <Text type="secondary" style={{ fontSize: 11 }}>
              <ClockCircleOutlined /> {relativeTime(a.occurredAt)}
            </Text>
          </Space>
        </Timeline.Item>
      ))}
    </Timeline>
  );

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card>
        {ticket.parentTicketId && (
          <div style={{ marginBottom: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Spawned from{' '}
              <a onClick={() => history.push(`/desk/${ticket.parentTicketId}`)}>
                #{ticket.parentTicketId}
              </a>
            </Text>
          </div>
        )}
        <Descriptions title={ticket.subject} column={2} size="small">
          <Descriptions.Item label="Status">
            <Tag color={STATUS_COLORS[ticket.status] || 'default'}>{ticket.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Priority">
            <Space>
              <Tag color={PRIORITY_COLORS[ticket.priority || 'NORMAL'] || 'default'}>
                {ticket.priority || 'NORMAL'}
              </Tag>
              <Select
                size="small"
                style={{ width: 100 }}
                value={ticket.priority || 'NORMAL'}
                onChange={handlePriorityChange}
                options={[
                  { label: 'Low', value: 'LOW' },
                  { label: 'Normal', value: 'NORMAL' },
                  { label: 'High', value: 'HIGH' },
                  { label: 'Urgent', value: 'URGENT' },
                ]}
              />
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Type">
            <Space>
              <Tag>{ticket.type || 'QUESTION'}</Tag>
              <Select
                size="small"
                style={{ width: 110 }}
                value={ticket.type || 'QUESTION'}
                onChange={handleTypeChange}
                options={[
                  { label: 'Question', value: 'QUESTION' },
                  { label: 'Incident', value: 'INCIDENT' },
                  { label: 'Problem', value: 'PROBLEM' },
                  { label: 'Task', value: 'TASK' },
                ]}
              />
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Channel">{ticket.platform} / {ticket.channelType}</Descriptions.Item>
          <Descriptions.Item label="Customer">{ticket.customerName || ticket.customerIdentifier}</Descriptions.Item>
          <Descriptions.Item label="Assignee">{ticket.assigneeId || '-'}</Descriptions.Item>
          <Descriptions.Item label="Created">{new Date(ticket.createdAt).toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="Updated">{new Date(ticket.updatedAt).toLocaleString()}</Descriptions.Item>
          {ticket.solvedAt && (
            <Descriptions.Item label="Solved">{new Date(ticket.solvedAt).toLocaleString()}</Descriptions.Item>
          )}
          {ticket.closedAt && (
            <Descriptions.Item label="Closed">{new Date(ticket.closedAt).toLocaleString()}</Descriptions.Item>
          )}
          {ticket.reopenCount !== undefined && ticket.reopenCount > 0 && (
            <Descriptions.Item label="Reopened">{ticket.reopenCount}x</Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      {/* Message thread + History tabs */}
      <Card>
        <Tabs
          defaultActiveKey="messages"
          items={[
            { key: 'messages', label: 'Messages', children: messagesTab },
            { key: 'history', label: 'History', children: historyTab },
          ]}
        />
      </Card>

      {/* Action bar — hidden for terminal statuses */}
      {!isTerminal && (
        <Card title="Reply">
          <Space direction="vertical" style={{ width: '100%' }}>
            {!isSolved && (
              <>
                <Space align="center">
                  <Switch
                    size="small"
                    checked={isInternal}
                    onChange={setIsInternal}
                  />
                  <Text type={isInternal ? 'warning' : 'secondary'} style={{ fontSize: 12 }}>
                    {isInternal ? 'Internal note (not sent to customer)' : 'Public reply'}
                  </Text>
                </Space>
                <TextArea
                  rows={4}
                  placeholder={isInternal ? 'Type internal note...' : 'Type your reply here...'}
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                  style={isInternal ? { background: '#fffbe6', borderColor: '#faad14' } : undefined}
                />
                <Space wrap>
                  <Button
                    type="primary"
                    loading={replying}
                    onClick={() => { setSolveAfter(false); handleReply(); }}
                    disabled={!replyContent.trim()}
                  >
                    {isInternal ? 'Save Note' : 'Reply'}
                  </Button>
                  {!isInternal && (
                    <Button
                      loading={replying}
                      onClick={() => { setSolveAfter(true); handleReply(); }}
                      disabled={!replyContent.trim()}
                    >
                      Reply & Solve
                    </Button>
                  )}
                  <Divider type="vertical" />
                  <Button onClick={handleAssignToMe}>Assign to Me</Button>
                  <Button onClick={handleSolve}>Solve</Button>
                  {isAdmin && (
                    <Dropdown overlay={adminMenu} trigger={['click']}>
                      <Button danger>
                        Admin Actions <DownOutlined />
                      </Button>
                    </Dropdown>
                  )}
                </Space>
              </>
            )}
            {isSolved && (
              <Space wrap>
                <Text type="secondary">
                  This ticket is solved. A new message from the customer will reopen it automatically.
                </Text>
                <Button onClick={handleSolve}>Re-Solve</Button>
                {isAdmin && (
                  <Dropdown overlay={adminMenu} trigger={['click']}>
                    <Button danger>
                      Admin Actions <DownOutlined />
                    </Button>
                  </Dropdown>
                )}
              </Space>
            )}
          </Space>
        </Card>
      )}
    </Space>
  );
}
