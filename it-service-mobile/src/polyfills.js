import { TextEncoder, TextDecoder } from 'text-encoding';

/**
 * React Native / Hermes ortamında eksik olabilen global'ler.
 * @stomp/stompjs (WebSocket istemcisi) TextEncoder/TextDecoder ister; yoksa
 * STOMP çerçevelerini seri hale getiremez ve bağlantı sessizce başarısız olur.
 * Bu modül index.js'te her şeyden önce import edilir.
 */
if (typeof global.TextEncoder === 'undefined') {
  global.TextEncoder = TextEncoder;
}
if (typeof global.TextDecoder === 'undefined') {
  global.TextDecoder = TextDecoder;
}
