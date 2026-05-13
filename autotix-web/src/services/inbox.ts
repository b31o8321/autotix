export interface InboxEvent {
  kind: 'TICKET_CREATED' | 'AI_REPLIED' | 'AGENT_REPLIED' | 'STATUS_CHANGED' | 'ASSIGNED' | 'NEW_MESSAGE';
  ticketId: string;
  channelId: string;
  summary: string;
  occurredAt: string;
}

export type InboxHandler = (event: InboxEvent) => void;

export function subscribeInbox(token: string, handler: InboxHandler): () => void {
  const es = new EventSource(`/api/inbox/stream?token=${encodeURIComponent(token)}`);
  const kinds: InboxEvent['kind'][] = [
    'TICKET_CREATED',
    'AI_REPLIED',
    'AGENT_REPLIED',
    'STATUS_CHANGED',
    'ASSIGNED',
    'NEW_MESSAGE',
  ];
  kinds.forEach((k) =>
    es.addEventListener(k, (e) => handler(JSON.parse((e as MessageEvent).data) as InboxEvent)),
  );
  es.onerror = () => {
    // EventSource will auto-reconnect; no manual backoff needed for v1
  };
  return () => es.close();
}
