import axios from 'axios';

export const apiClient = axios.create({
  baseURL: 'http://localhost:8123/api',
  timeout: 30000,
  withCredentials: true,
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

export function getKnowledgeGraphHealth() {
  return apiClient.get('/knowledge/graph/health');
}

export function getKnowledgeGraphStats() {
  return apiClient.get('/knowledge/graph/stats');
}

export function searchKnowledgeGraph(keyword, limit = 100) {
  return apiClient.get('/knowledge/graph/search', {
    params: { keyword, limit },
    timeout: 120000,
  });
}

export function visualizeKnowledgeGraph(keyword, depth = 1, limit = 300) {
  return apiClient.get('/knowledge/graph/visualize', {
    params: { keyword, depth, limit },
    timeout: 120000,
  });
}

export function getKnowledgeGraphNode(nodeId) {
  return apiClient.get(`/knowledge/graph/nodes/${encodeURIComponent(nodeId)}`);
}

export function getKnowledgeGraphRelationship(relationshipId) {
  return apiClient.get(`/knowledge/graph/relationships/${encodeURIComponent(relationshipId)}`);
}

export function registerUser(userAccount, userPassword, checkPassword) {
  return apiClient.post('/user/register', {
    user_account: userAccount,
    user_password: userPassword,
    checkpassword: checkPassword,
  });
}

export function loginUser(userAccount, userPassword) {
  return apiClient.post('/user/login', {
    user_account: userAccount,
    user_password: userPassword,
  });
}

export function getCurrentUser() {
  return apiClient.get('/user/current');
}

export function searchUsers(username = '') {
  return apiClient.get('/user/search', {
    params: { username },
  });
}

export function deleteUser(userId) {
  return apiClient.post('/user/delete', {
    id: userId,
  });
}

export function logoutUser() {
  return apiClient.post('/user/logout');
}
