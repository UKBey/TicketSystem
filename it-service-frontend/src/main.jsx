import './i18n'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import { DateFormatProvider } from './context/DateFormatContext'
import { PanelPrefsProvider } from './context/PanelPrefsContext'
import { CommandPaletteProvider } from './context/CommandPaletteContext'
import { ToastProvider } from './context/ToastContext'
import App from './App.jsx'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ThemeProvider>
      <ToastProvider>
        <DateFormatProvider>
          <PanelPrefsProvider>
            <AuthProvider>
              <CommandPaletteProvider>
                <App />
              </CommandPaletteProvider>
            </AuthProvider>
          </PanelPrefsProvider>
        </DateFormatProvider>
      </ToastProvider>
    </ThemeProvider>
  </StrictMode>,
)
