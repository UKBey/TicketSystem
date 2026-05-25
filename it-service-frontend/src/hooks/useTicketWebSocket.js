import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import keycloak from '../keycloak';

// Ticket detay sayfasi icin STOMP/WebSocket baglantisi kurar.
// Backend `/topic/tickets/{id}` (herkes) ve `/topic/tickets/{id}/internal`
// (sadece agent/admin) topic'lerine yayın yapar.
export function useTicketWebSocket(ticketId, { onComment, onAttachment, onTicketUpdated, includeInternal } = {}) {
  const clientRef = useRef(null);
  const handlersRef = useRef({ onComment, onAttachment, onTicketUpdated });

  useEffect(() => {
    handlersRef.current = { onComment, onAttachment, onTicketUpdated };
  }, [onComment, onAttachment, onTicketUpdated]);

  useEffect(() => {
    if (!ticketId) return undefined;

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const brokerURL = `${protocol}://${window.location.host}/api/v1/ws`;

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

    const client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        try {
          await keycloak.updateToken(30);
        } catch {
          // Refresh basarisiz olsa bile mevcut token ile dene.
        }
        client.connectHeaders = {
          Authorization: `Bearer ${keycloak.token}`,
        };
      },
      onConnect: () => {
        client.subscribe(`/topic/tickets/${ticketId}`, handleEvent);
        if (includeInternal) {
          client.subscribe(`/topic/tickets/${ticketId}/internal`, handleEvent);
        }
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'], frame.body);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [ticketId, includeInternal]);
}
