/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext } from 'react';

const TicketDetailContext = createContext(null);

export function TicketDetailProvider({ value, children }) {
  return <TicketDetailContext.Provider value={value}>{children}</TicketDetailContext.Provider>;
}

export function useTicketDetailContext() {
  const ctx = useContext(TicketDetailContext);
  if (!ctx) {
    throw new Error('useTicketDetailContext must be used within a TicketDetailProvider');
  }
  return ctx;
}
