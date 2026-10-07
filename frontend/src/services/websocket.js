import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

class WebSocketService {
  constructor() {
    this.client = null;
    this.subscribers = new Map(); // topic -> Set of callbacks
    this.isConnected = false;
    this.reconnectTimeout = null;
  }

  connect(onConnectCallback, onErrorCallback) {
    if (this.client && this.isConnected) {
      if (onConnectCallback) onConnectCallback();
      return;
    }

    const wsUrl = import.meta.env.VITE_WS_URL || '/ws';

    this.client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg) => {
        // Suppress noisy heartbeat logs in production
        if (import.meta.env.DEV && !msg.includes('PING') && !msg.includes('PONG')) {
          console.debug('[STOMP]', msg);
        }
      },
      onConnect: (frame) => {
        this.isConnected = true;
        console.log('[STOMP] Connected to Medical Drone Telemetry WebSocket', frame);
        
        // Re-subscribe any existing listeners
        this.subscribers.forEach((callbacks, topic) => {
          this.subscribeToTopic(topic);
        });

        if (onConnectCallback) onConnectCallback();
      },
      onStompError: (frame) => {
        this.isConnected = false;
        console.error('[STOMP Error]', frame.headers['message'], frame.body);
        if (onErrorCallback) onErrorCallback(frame);
      },
      onWebSocketClose: () => {
        this.isConnected = false;
        console.warn('[STOMP] Connection closed. Attempting reconnect...');
      }
    });

    this.client.activate();
  }

  subscribe(topic, callback) {
    if (!this.subscribers.has(topic)) {
      this.subscribers.set(topic, new Set());
      if (this.isConnected) {
        this.subscribeToTopic(topic);
      }
    }
    this.subscribers.get(topic).add(callback);

    // Return un-subscriber
    return () => {
      const callbacks = this.subscribers.get(topic);
      if (callbacks) {
        callbacks.delete(callback);
        if (callbacks.size === 0) {
          this.subscribers.delete(topic);
        }
      }
    };
  }

  subscribeToTopic(topic) {
    if (!this.client || !this.isConnected) return;

    this.client.subscribe(topic, (message) => {
      try {
        const payload = JSON.parse(message.body);
        const callbacks = this.subscribers.get(topic);
        if (callbacks) {
          callbacks.forEach((cb) => {
            try {
              cb(payload);
            } catch (err) {
              console.error(`[STOMP] Error in callback for ${topic}:`, err);
            }
          });
        }
      } catch (err) {
        console.error(`[STOMP] Failed to parse message on ${topic}:`, message.body);
      }
    });
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.isConnected = false;
    }
  }
}

export const wsService = new WebSocketService();
export default wsService;
