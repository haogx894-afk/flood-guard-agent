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

export function getKnowledgeDocuments() {
  return apiClient.get('/knowledge/documents');
}

export function uploadKnowledgeDocument(file, replaceSameName = true) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('replaceSameName', String(replaceSameName));

  return apiClient.post('/knowledge/documents/upload', formData, {
    timeout: 120000,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}

export function rebuildKnowledgeDocument(documentId) {
  return apiClient.post(`/knowledge/documents/${documentId}/rebuild`, null, {
    timeout: 120000,
  });
}

export function deleteKnowledgeDocument(documentId) {
  return apiClient.delete(`/knowledge/documents/${documentId}`);
}
