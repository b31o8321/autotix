# OAuth Roadmap

## Current state (v1)

All OAuth-declared platforms (currently only Zendesk) return HTTP 501 when the frontend
calls `POST /api/admin/channels/oauth/start`.

The frontend shows a friendly alert and falls back to manual API key / app-credentials entry.

## What needs wiring to add real Zendesk OAuth

### 1 — Plugin: override two methods in `ZendeskPlugin`

```java
@Override
public String startOAuth(String state) {
    // Build and return the Zendesk authorize URL
    // e.g. https://{subdomain}.zendesk.com/oauth/authorizations/new
    //      ?client_id=...&redirect_uri=...&state=...&scope=read%20write
    return zendeskClient.buildAuthorizeUrl(state, redirectUri);
}

@Override
public ChannelCredential exchangeCode(String code) {
    // POST to https://{subdomain}.zendesk.com/oauth/tokens
    // Returns access_token; wrap in ChannelCredential
    return zendeskClient.oauthExchange(code, redirectUri);
}
```

### 2 — ZendeskClient: implement two stubs

Both methods exist with `// TODO` stubs:
- `buildAuthorizeUrl(state, redirectUri)` — construct URL with query params
- `oauthExchange(code, redirectUri)` — POST form body, parse JSON response,
  return `new ChannelCredential(accessToken, null, null, Map.of("subdomain", subdomain))`

### 3 — ConnectChannelUseCase: thread subdomain + redirectUri into startOAuth

Currently `startOAuth` is called with just `state`. The use case needs to:
1. Look up the registered plugin for the platform
2. Call `plugin.startOAuth(state)` — the plugin knows its own subdomain from the request params

The `OAuthStartRequest` DTO already carries `platform`, `channelType`, `displayName`.
Add `subdomain` and `clientId` fields to let the use case pass them to the plugin
(or store them in a short-lived Redis/DB state keyed by `state`).

## Sequence (after wiring)

```
FE → POST /oauth/start {platform, subdomain, clientId, clientSecret, displayName}
Server → store state → plugin.startOAuth(state) → returns authorizeUrl
FE → window.location.href = authorizeUrl
Zendesk → GET /oauth/callback?code=...&state=...
Server → plugin.exchangeCode(code) → ChannelCredential
Server → save channel → redirect FE to /settings/channels/ZENDESK
```

## Estimated effort

| Task | Effort |
|---|---|
| ZendeskClient stubs → real HTTP | 2 h |
| ZendeskPlugin.startOAuth / exchangeCode | 1 h |
| OAuthStartRequest + state store (in-memory Map is fine for v1) | 1 h |
| Frontend: remove 501 alert, handle real redirect | 30 min |

Total: ~4–5 hours per OAuth platform.
