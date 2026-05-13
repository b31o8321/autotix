// TODO: AI configuration page.
//   Form fields: endpoint, apiKey (password), model, systemPrompt (textarea),
//                timeoutSeconds, maxRetries
//   Buttons:
//     - [Test connection] -> calls /api/admin/ai/test, shows latency + sample reply
//     - [Save] -> PUT /api/admin/ai
import { Card } from 'antd';

export default function AIConfigPage() {
  return <Card title="AI Configuration">{/* TODO: antd Form */}</Card>;
}
