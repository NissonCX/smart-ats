import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuthStore } from '../store/auth';

interface UseSseOptions {
  /** SSE endpoint URL (absolute or relative) */
  url: string;
  /** Auto-reconnect on error (default: true) */
  autoReconnect?: boolean;
  /** Reconnect delay in ms (default: 3000) */
  reconnectDelay?: number;
  /** Max reconnection attempts (default: 10) */
  maxRetries?: number;
  /** Callback when a message event is received */
  onMessage?: (data: string) => void;
  /** Callback when connection opens */
  onOpen?: () => void;
  /** Callback on error */
  onError?: (event: Event) => void;
  /** Whether to start connected (default: true) */
  enabled?: boolean;
}

interface UseSseReturn {
  /** Current connection status */
  status: 'connecting' | 'connected' | 'disconnected' | 'error';
  /** Last received data string */
  lastData: string | null;
  /** Manually reconnect */
  reconnect: () => void;
  /** Manually disconnect */
  disconnect: () => void;
}

/**
 * Custom hook to manage an SSE (EventSource) connection with auto-reconnect.
 *
 * Note: Since EventSource does not support custom headers, we pass the JWT token
 * as a query param `?token=xxx`. The backend SSE endpoint should accept this.
 * If the backend reads from the standard Authorization header instead, you can use
 * a polyfill like `eventsource-polyfill`. For simplicity, we just rely on cookies
 * or make the SSE endpoint accept tokens via query params.
 *
 * Current implementation: We append the access token as a query parameter.
 */
export function useSse({
  url,
  autoReconnect = true,
  reconnectDelay = 3000,
  maxRetries = 10,
  onMessage,
  onOpen,
  onError,
  enabled = true,
}: UseSseOptions): UseSseReturn {
  const [status, setStatus] = useState<UseSseReturn['status']>('disconnected');
  const [lastData, setLastData] = useState<string | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const retryCountRef = useRef(0);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cleanup = useCallback(() => {
    if (retryTimerRef.current) {
      clearTimeout(retryTimerRef.current);
      retryTimerRef.current = null;
    }
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  const connect = useCallback(() => {
    cleanup();

    const token = useAuthStore.getState().accessToken;
    const separator = url.includes('?') ? '&' : '?';
    const fullUrl = token ? `${url}${separator}token=${encodeURIComponent(token)}` : url;

    setStatus('connecting');

    const es = new EventSource(fullUrl);
    eventSourceRef.current = es;

    es.onopen = () => {
      setStatus('connected');
      retryCountRef.current = 0;
      onOpen?.();
    };

    es.onmessage = (event) => {
      setLastData(event.data);
      onMessage?.(event.data);
    };

    es.onerror = (event) => {
      setStatus('error');
      onError?.(event);
      es.close();
      eventSourceRef.current = null;

      if (autoReconnect && retryCountRef.current < maxRetries) {
        retryCountRef.current += 1;
        retryTimerRef.current = setTimeout(() => {
          connect();
        }, reconnectDelay);
      } else {
        setStatus('disconnected');
      }
    };
  }, [url, autoReconnect, reconnectDelay, maxRetries, onMessage, onOpen, onError, cleanup]);

  const disconnect = useCallback(() => {
    cleanup();
    retryCountRef.current = 0;
    setStatus('disconnected');
  }, [cleanup]);

  const reconnect = useCallback(() => {
    retryCountRef.current = 0;
    connect();
  }, [connect]);

  useEffect(() => {
    if (enabled) {
      connect();
    }
    return () => {
      cleanup();
    };
  }, [enabled, connect, cleanup]);

  return { status, lastData, reconnect, disconnect };
}
