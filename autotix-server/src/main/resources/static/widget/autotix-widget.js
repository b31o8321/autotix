/**
 * Autotix LiveChat Widget
 * Vanilla JS — no dependencies. Embed with:
 *   <script src="/widget/autotix-widget.js" data-channel-token="TOKEN" async></script>
 */
(function () {
  'use strict';

  // ---- Config ---------------------------------------------------------------
  var scriptTag = document.currentScript ||
    (function () {
      var scripts = document.getElementsByTagName('script');
      return scripts[scripts.length - 1];
    })();

  var TOKEN = scriptTag ? scriptTag.getAttribute('data-channel-token') : null;
  if (!TOKEN) {
    console.warn('[Autotix] data-channel-token not set on script tag — widget disabled.');
    return;
  }

  var SESSION_KEY = 'autotix_session_' + TOKEN;
  var sessionId = localStorage.getItem(SESSION_KEY);
  if (!sessionId) {
    sessionId = 'sess-' + Math.random().toString(36).slice(2) + '-' + Date.now();
    localStorage.setItem(SESSION_KEY, sessionId);
  }

  var PRIMARY = '#2962FF';
  var WS_URL = (location.protocol === 'https:' ? 'wss://' : 'ws://') +
    location.host + '/ws/livechat/' + TOKEN + '/' + sessionId;

  // ---- State ----------------------------------------------------------------
  var ws = null;
  var reconnectDelay = 1000;
  var maxDelay = 30000;
  var panelOpen = false;
  var sentHello = false;

  // ---- DOM ------------------------------------------------------------------
  var style = document.createElement('style');
  style.textContent = [
    '#atx-bubble{position:fixed;bottom:24px;right:24px;width:56px;height:56px;border-radius:50%;background:' + PRIMARY + ';cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,.25);display:flex;align-items:center;justify-content:center;z-index:99999;border:none;outline:none;}',
    '#atx-bubble svg{fill:#fff;width:28px;height:28px;}',
    '#atx-panel{position:fixed;bottom:92px;right:24px;width:360px;height:500px;background:#fff;border-radius:12px;box-shadow:0 8px 32px rgba(0,0,0,.18);display:none;flex-direction:column;z-index:99998;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;overflow:hidden;}',
    '#atx-panel.open{display:flex;}',
    '#atx-header{background:' + PRIMARY + ';color:#fff;padding:14px 16px;font-size:15px;font-weight:600;display:flex;align-items:center;justify-content:space-between;}',
    '#atx-close-btn{background:none;border:none;color:#fff;cursor:pointer;font-size:20px;line-height:1;padding:0;}',
    '#atx-messages{flex:1;overflow-y:auto;padding:12px;display:flex;flex-direction:column;gap:8px;}',
    '.atx-msg{max-width:80%;padding:8px 12px;border-radius:12px;font-size:14px;line-height:1.45;word-break:break-word;}',
    '.atx-msg.customer{align-self:flex-end;background:' + PRIMARY + ';color:#fff;border-bottom-right-radius:4px;}',
    '.atx-msg.agent{align-self:flex-start;background:#f0f0f0;color:#222;border-bottom-left-radius:4px;}',
    '.atx-msg .atx-author{font-size:11px;font-weight:600;margin-bottom:3px;opacity:.7;}',
    '#atx-footer{padding:10px;border-top:1px solid #e8e8e8;display:flex;gap:8px;}',
    '#atx-input{flex:1;border:1px solid #ddd;border-radius:8px;padding:8px 10px;font-size:14px;outline:none;resize:none;height:40px;line-height:1.4;font-family:inherit;}',
    '#atx-send{background:' + PRIMARY + ';color:#fff;border:none;border-radius:8px;padding:0 16px;cursor:pointer;font-size:14px;height:40px;white-space:nowrap;}',
    '#atx-send:hover{opacity:.85;}',
    '#atx-status-bar{font-size:11px;text-align:center;padding:4px;color:#999;}',
  ].join('\n');
  document.head.appendChild(style);

  // Bubble
  var bubble = document.createElement('button');
  bubble.id = 'atx-bubble';
  bubble.innerHTML = '<svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z"/></svg>';
  bubble.title = 'Chat with us';
  document.body.appendChild(bubble);

  // Panel
  var panel = document.createElement('div');
  panel.id = 'atx-panel';
  panel.innerHTML = [
    '<div id="atx-header">',
    '  <span>Support Chat</span>',
    '  <button id="atx-close-btn" title="Close">&times;</button>',
    '</div>',
    '<div id="atx-messages"></div>',
    '<div id="atx-status-bar">Connecting...</div>',
    '<div id="atx-footer">',
    '  <textarea id="atx-input" placeholder="Type a message..." rows="1"></textarea>',
    '  <button id="atx-send">Send</button>',
    '</div>',
  ].join('');
  document.body.appendChild(panel);

  var msgList = document.getElementById('atx-messages');
  var input = document.getElementById('atx-input');
  var sendBtn = document.getElementById('atx-send');
  var statusBar = document.getElementById('atx-status-bar');
  var closeBtn = document.getElementById('atx-close-btn');

  // ---- UI helpers -----------------------------------------------------------
  function togglePanel() {
    panelOpen = !panelOpen;
    panel.classList.toggle('open', panelOpen);
    if (panelOpen) {
      if (!ws || ws.readyState === WebSocket.CLOSED || ws.readyState === WebSocket.CLOSING) {
        connect();
      }
      input.focus();
      scrollToBottom();
    }
  }

  function appendMessage(who, content, authorLabel) {
    var div = document.createElement('div');
    div.className = 'atx-msg ' + who;
    if (authorLabel) {
      var auth = document.createElement('div');
      auth.className = 'atx-author';
      auth.textContent = authorLabel;
      div.appendChild(auth);
    }
    var body = document.createElement('div');
    body.textContent = content;
    div.appendChild(body);
    msgList.appendChild(div);
    scrollToBottom();
  }

  function scrollToBottom() {
    msgList.scrollTop = msgList.scrollHeight;
  }

  function setStatus(txt) {
    statusBar.textContent = txt;
  }

  // ---- WebSocket ------------------------------------------------------------
  function connect() {
    setStatus('Connecting...');
    try {
      ws = new WebSocket(WS_URL);
    } catch (e) {
      setStatus('Connection failed. Retrying...');
      scheduleReconnect();
      return;
    }

    ws.onopen = function () {
      reconnectDelay = 1000;
      setStatus('Connected');
      if (!sentHello) {
        sentHello = true;
        ws.send(JSON.stringify({ type: 'hello' }));
      }
    };

    ws.onmessage = function (evt) {
      var frame;
      try { frame = JSON.parse(evt.data); } catch (e) { return; }
      handleFrame(frame);
    };

    ws.onclose = function () {
      setStatus('Disconnected. Reconnecting...');
      scheduleReconnect();
    };

    ws.onerror = function () {
      setStatus('Connection error.');
    };
  }

  function scheduleReconnect() {
    var delay = reconnectDelay;
    reconnectDelay = Math.min(reconnectDelay * 2, maxDelay);
    setTimeout(function () {
      if (!ws || ws.readyState === WebSocket.CLOSED) {
        connect();
      }
    }, delay);
  }

  function handleFrame(frame) {
    switch (frame.type) {
      case 'ready':
        setStatus('Connected');
        break;
      case 'message':
        appendMessage('agent', frame.content, frame.author || 'AI');
        break;
      case 'agent_message':
        appendMessage('agent', frame.content, frame.author || 'Agent');
        break;
      case 'status':
        setStatus('Status: ' + frame.status);
        break;
      case 'error':
        setStatus('Error: ' + frame.message);
        break;
      default:
        break;
    }
  }

  function sendMessage() {
    var text = input.value.trim();
    if (!text) return;
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      setStatus('Not connected. Reconnecting...');
      connect();
      return;
    }
    ws.send(JSON.stringify({ type: 'message', content: text }));
    appendMessage('customer', text, null);
    input.value = '';
    input.style.height = '40px';
  }

  // ---- Event listeners ------------------------------------------------------
  bubble.addEventListener('click', togglePanel);
  closeBtn.addEventListener('click', togglePanel);

  sendBtn.addEventListener('click', sendMessage);

  input.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });

  // Auto-resize textarea
  input.addEventListener('input', function () {
    input.style.height = '40px';
    input.style.height = Math.min(input.scrollHeight, 100) + 'px';
  });

  // ---- Init -----------------------------------------------------------------
  connect();

})();
