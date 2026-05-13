// TODO: Single ticket detail.
//   - Header: subject, customer, channel badge, status, assignee, tags
//   - Message thread (INBOUND left, OUTBOUND right, AI badge for ai-generated)
//   - Action bar: reply (markdown editor), assign, close
import { Card } from 'antd';
import { useParams } from 'umi';

export default function TicketDetail() {
  const { ticketId } = useParams();
  // TODO: useEffect getTicket(ticketId); render thread + actions
  return <Card title={`Ticket ${ticketId}`}>{/* TODO */}</Card>;
}
