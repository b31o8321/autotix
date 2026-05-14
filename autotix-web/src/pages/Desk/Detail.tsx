import { useEffect, useState } from 'react';
import { Card, Tag, Space, Descriptions, Button, Input, message, Spin, Divider, Typography, Dropdown, Menu } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import { useParams, history } from 'umi';
import { getTicket, replyTicket, solveTicket, closeTicket, assignTicket, type TicketDTO, type MessageDTO } from '@/services/ticket';
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

/** Statuses where the reply / action bar should be hidden */
const TERMINAL_STATUSES = ['CLOSED', 'SPAM'];

export default function TicketDetail() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const [ticket, setTicket] = useState<TicketDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [replyContent, setReplyContent] = useState('');
  const [solveAfter, setSolveAfter] = useState(false);
  const [replying, setReplying] = useState(false);

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

  useEffect(() => {
    fetchTicket();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId]);

  async function handleReply() {
    if (!ticketId || !replyContent.trim()) return;
    setReplying(true);
    try {
      await replyTicket(ticketId, replyContent, solveAfter);
      setReplyContent('');
      message.success(solveAfter ? 'Replied and solved' : 'Replied');
      await fetchTicket();
    } catch {
      message.error('Reply failed');
    } finally {
      setReplying(false);
    }
  }

  async function handleSolve() {
    if (!ticketId) return;
    try {
      await solveTicket(ticketId);
      message.success('Ticket solved');
      await fetchTicket();
    } catch {
      message.error('Solve failed');
    }
  }

  async function handlePermanentClose() {
    if (!ticketId) return;
    try {
      await closeTicket(ticketId);
      message.success('Ticket permanently closed');
      await fetchTicket();
    } catch {
      message.error('Close failed');
    }
  }

  async function handleAssignToMe() {
    if (!ticketId || !currentUser) return;
    try {
      await assignTicket(ticketId, currentUser.id);
      message.success('Assigned to you');
      await fetchTicket();
    } catch {
      message.error('Assign failed');
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

      {/* Message thread */}
      <Card title="Messages">
        <Space direction="vertical" style={{ width: '100%' }} size="small">
          {(ticket.messages || []).map((msg: MessageDTO, idx: number) => {
            const isOutbound = msg.direction === 'OUTBOUND';
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
                    background: isOutbound
                      ? isAI
                        ? '#e6f4ff'
                        : '#f0f5ff'
                      : '#fafafa',
                    border: isAI ? '1px solid #91caff' : undefined,
                  }}
                  bodyStyle={{ padding: '8px 12px' }}
                >
                  <Space size="small">
                    {isAI && <Tag color="blue" style={{ fontSize: 10 }}>AI</Tag>}
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
      </Card>

      {/* Action bar — hidden for terminal statuses */}
      {!isTerminal && (
        <Card title="Reply">
          <Space direction="vertical" style={{ width: '100%' }}>
            {!isSolved && (
              <>
                <TextArea
                  rows={4}
                  placeholder="Type your reply here..."
                  value={replyContent}
                  onChange={(e) => setReplyContent(e.target.value)}
                />
                <Space wrap>
                  <Button
                    type="primary"
                    loading={replying}
                    onClick={() => { setSolveAfter(false); handleReply(); }}
                    disabled={!replyContent.trim()}
                  >
                    Reply
                  </Button>
                  <Button
                    loading={replying}
                    onClick={() => { setSolveAfter(true); handleReply(); }}
                    disabled={!replyContent.trim()}
                  >
                    Reply & Solve
                  </Button>
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
