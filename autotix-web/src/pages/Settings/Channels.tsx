// TODO: Channels page.
//   - Table of connected channels (platform, channelType, displayName, webhookToken, autoReply toggle, ...)
//   - "Add Channel" wizard: pick platform -> OAuth redirect OR API-key form
//   - Per-row actions: rotate webhook, rename, disconnect, hard-delete
import { Card } from 'antd';

export default function ChannelsPage() {
  // TODO: useEffect listChannels; modal for add wizard
  return <Card title="Channels">{/* TODO */}</Card>;
}
