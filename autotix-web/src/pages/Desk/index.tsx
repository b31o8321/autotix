import { useEffect, useState } from 'react';
import { Table, Select, Input, Button, Tag, Space, Row, Col } from 'antd';
import { history } from 'umi';
import type { ColumnsType } from 'antd/es/table';
import { listTickets, type TicketDTO } from '@/services/ticket';
import { listChannels, type ChannelDTO } from '@/services/channel';

const STATUS_COLORS: Record<string, string> = {
  OPEN: 'blue',
  PENDING: 'orange',
  ASSIGNED: 'purple',
  CLOSED: 'default',
};

const PAGE_SIZE = 20;

export default function DeskPage() {
  const [tickets, setTickets] = useState<TicketDTO[]>([]);
  const [channels, setChannels] = useState<ChannelDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [channelId, setChannelId] = useState<string | undefined>(undefined);
  const [search, setSearch] = useState('');
  const [offset, setOffset] = useState(0);

  async function fetchTickets() {
    setLoading(true);
    try {
      const data = await listTickets({
        status,
        channelId,
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
  }, [status, channelId, offset]);

  const columns: ColumnsType<TicketDTO> = [
    {
      title: 'Subject',
      dataIndex: 'subject',
      key: 'subject',
      ellipsis: true,
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
              { label: 'Open', value: 'OPEN' },
              { label: 'Pending', value: 'PENDING' },
              { label: 'Assigned', value: 'ASSIGNED' },
              { label: 'Closed', value: 'CLOSED' },
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
