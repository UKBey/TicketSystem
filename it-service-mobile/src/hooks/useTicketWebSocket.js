import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { API_BASE_URL } from '../config';
import { getAuthToken } from '../api/client';

/**
 * Bilet detayı için STOMP/WebSocket bağlantısı — gerçek zamanlı yorum, ek ve
 * bilet güncellemelerini dinler. Backend `/topic/tickets/{id}` (herkes) ve
 * `/topic/tickets/{id}/internal` (yalnızca agent) topic'lerine yayın yapar.
 * Bağlantı kurulamazsa uygulama çalışmaya devam eder — gerçek zamanlı akış opsiyoneldir.
 */
export function useTicketWebSocket(
  ticketId,
  { onComment, onAttachment, onTicketUpdated, includeInternal } = {},
) {
  const handlersRef = useRef({ onComment, onAttachment, onTicketUpdated });

  useEffect(() => {
    handlersRef.current = { onComment, onAttachment, onTicketUpdated };
  }, [onComment, onAttachment, onTicketUpdated]);

  useEffect(() => {
    if (!ticketId) return undefined;

    const brokerURL = `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;

    const handleEvent = (message) => {
      let event;
      try {
        event = JSON.parse(message.body);
      } catch {
        return;
      }
      const { onComment: oc, onAttachment: oa, onTicketUpdated: ot } = handlersRef.current;
      if (event.type === 'COMMENT_ADDED' && oc) oc(event.payload);
      else if (event.type === 'ATTACHMENT_ADDED' && oa) oa(event.payload);
      else if (event.type === 'TICKET_UPDATED' && ot) ot();
    };

    let client;
    try {
      client = new Client({
        brokerURL,
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        beforeConnect: () => {
          client.connectHeaders = { Authorization: `Bearer ${getAuthToken() || ''}` };
        },
        onConnect: () => {
          client.subscribe(`/topic/tickets/${ticketId}`, handleEvent);
          if (includeInternal) {
            client.subscribe(`/topic/tickets/${ticketId}/internal`, handleEvent);
          }
        },
        // Hata olsa da uygulama çalışmaya devam eder — polling yedeği devrede.
        onStompError: () => {},
        onWebSocketError: () => {},
      });
      client.activate();
    } catch {
      return undefined;
    }

    return () => {
      try {
        client.deactivate();
      } catch {
        // yoksay
      }
    };
  }, [ticketId, includeInternal]);
}
