import axios from 'axios';

export const apiClient = axios.create({
  baseURL: 'http://localhost:8123/api',
  timeout: 30000,
});

export function buildManusSseUrl(message, chatId) {
  const url = apiClient.getUri({
    url: '/ai/manus/chat',
    params: { message, chatId },
  });

  if (url.startsWith('http')) {
    return url;
  }

  return new URL(url, apiClient.defaults.baseURL).toString();
}
