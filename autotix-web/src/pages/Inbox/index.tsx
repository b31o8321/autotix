// TODO: Inbox — real-time view via SSE.
//   - subscribeInbox(token, handler) on mount; cleanup on unmount
//   - List grows with incoming events; click to open ticket detail
//   - "Take over" action -> assignTicket(ticketId, currentUserId)
import { Card } from 'antd';

export default function InboxPage() {
  // TODO: useEffect: const unsub = subscribeInbox(token, e => setEvents(prev => [e, ...prev]));
  //                  return unsub;
  return <Card title="Inbox (Live)">{/* TODO: event stream list */}</Card>;
}
