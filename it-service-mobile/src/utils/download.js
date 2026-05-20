import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import { getAuthToken } from '../api/client';
import { API_BASE_URL } from '../config';

/**
 * Bir dosya ekini auth header'ı ile indirir, cache'e yazar ve paylaşım/açma
 * sayfasını gösterir. axios kullanılmaz — ikili içerik için FileSystem gerekir.
 */
export async function downloadAttachment(attachment) {
  const token = getAuthToken();
  const safeName = String(attachment.fileName || `file-${attachment.id}`).replace(/[/\\]/g, '_');
  const target = `${FileSystem.cacheDirectory}${attachment.id}_${safeName}`;

  const result = await FileSystem.downloadAsync(
    `${API_BASE_URL}/attachments/${attachment.id}`,
    target,
    { headers: token ? { Authorization: `Bearer ${token}` } : {} },
  );

  if (result.status !== 200) {
    throw new Error(`download failed: ${result.status}`);
  }

  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(result.uri, {
      mimeType: attachment.fileType || undefined,
      dialogTitle: attachment.fileName,
    });
  }
  return result.uri;
}
