import { useEffect, useState } from 'react';
import { Table, Select, Input, Button, Tag, Space, Row, Col, Badge } from 'antd';
import { history } from 'umi';
import type { ColumnsType } from 'antd/es/table';
import { listTickets, type TicketDTO } from '@/services/ticket';
import { listChannels, type ChannelDTO } from '@/services/channel';

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

const PAGE_SIZE = 20;

export default function DeskPage() {
  const [tickets, setTickets] = useState<TicketDTO[]>([]);
  const [channels, setChannels] = useState<ChannelDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [channelId, setChannelId] = useState<string | undefined>(undefined);
  const [priority, setPriority] = useState<string | undefined>(undefined);
  const [search, setSearch] = useState('');
  const [offset, setOffset] = useState(0);

  async function fetchTickets() {
    setLoading(true);
    try {
      const data = await listTickets({
        status,
        channelId,
        priority,
        q: search || undefined,
        offset,
        limit: PAGE_SIZE,
      });
      setTickets(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    listChannels()
      .then(setChannels)
      .catch(() => {});
  }, []);

  useEffect(() => {
    fetchTickets();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, channelId, priority, offset]);

  const columns: ColumnsType<TicketDTO> = [
    {
      title: 'Subject',
      dataIndex: 'subject',
      key: 'subject',
      ellipsis: true,
      render: (subject: string, record: TicketDTO) => (
        <Space size="small">
          {record.slaBreached && (
            <Badge color="red" title="SLA Breached" />
          )}
          {subject}
        </Space>
      ),
    },
    {
      title: 'Customer',
      key: 'customer',
      render: (_: unknown, r: TicketDTO) => r.customerName || r.customerIdentifier,
    },
    {
      title: 'Channel',
      key: 'channel',
      render: (_: unknown, r: TicketDTO) => {
        const ch = channels.find((c) => c.id === r.channelId);
        return ch?.displayName || r.channelId;
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Priority',
      dataIndex: 'priority',
      key: 'priority',
      render: (p: string) => {
        const val = p || 'NORMAL';
        return <Tag color={PRIORITY_COLORS[val] || 'default'}>{val}</Tag>;
      },
    },
    {
      title: 'Assignee',
      dataIndex: 'assigneeId',
      key: 'assigneeId',
      render: (v: string | undefined) => v || '-',
    },
    {
      title: 'Updated',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (v: string) => new Date(v).toLocaleString(),
      width: 160,
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Row gutter={8} align="middle">
        <Col>
          <Select
            placeholder="Status"
            allowClear
            style={{ width: 130 }}
            value={status}
            onChange={(v) => { setStatus(v); setOffset(0); }}
            options={[
              { label: 'New', value: 'NEW' },
              { label: 'Open', value: 'OPEN' },
              { label: 'Waiting on Customer', value: 'WAITING_ON_CUSTOMER' },
              { label: 'Waiting on Internal', value: 'WAITING_ON_INTERNAL' },
              { label: 'Solved', value: 'SOLVED' },
              { label: 'Closed', value: 'CLOSED' },
              { label: 'Spam', value: 'SPAM' },
            ]}
          />
        </Col>
        <Col>
          <Select
            placeholder="Priority"
            allowClear
            style={{ width: 110 }}
            value={priority}
            onChange={(v) => { setPriority(v); setOffset(0); }}
            options={[
              { label: 'Low', value: 'LOW' },
              { label: 'Normal', value: 'NORMAL' },
              { label: 'High', value: 'HIGH' },
              { label: 'Urgent', value: 'URGENT' },
            ]}
          />
        </Col>
        <Col>
          <Select
            placeholder="Channel"
            allowClear
            style={{ width: 180 }}
            value={channelId}
            onChange={(v) => { setChannelId(v); setOffset(0); }}
            options={channels.map((c) => ({ label: c.displayName, value: c.id }))}
          />
        </Col>
        <Col flex="auto">
          <Input.Search
            placeholder="Search tickets..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={() => { setOffset(0); fetchTickets(); }}
            allowClear
          />
        </Col>
        <Col>
          <Button onClick={() => { setOffset(0); fetchTickets(); }}>Refresh</Button>
        </Col>
      </Row>

      <Table<TicketDTO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={tickets}
        pagination={{
          pageSize: PAGE_SIZE,
          current: Math.floor(offset / PAGE_SIZE) + 1,
          onChange: (page) => setOffset((page - 1) * PAGE_SIZE),
          showSizeChanger: false,
        }}
        onRow={(r) => ({
          onClick: () => history.push(`/desk/${r.id}`),
          style: { cursor: 'pointer' },
        })}
      />
    </Space>
  );
}
