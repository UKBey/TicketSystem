import api from './api';

async function updateProfile({ firstName, lastName, email }) {
  const response = await api.put('/users/me', { firstName, lastName, email });
  return response.data;
}

async function changePassword({ currentPassword, newPassword }) {
  await api.post('/users/me/password', { currentPassword, newPassword });
}

async function listTotpDevices() {
  const response = await api.get('/users/me/2fa');
  return response.data;
}

async function deleteTotpDevice(credentialId) {
  await api.delete(`/users/me/2fa/${credentialId}`);
}

export default {
  updateProfile,
  changePassword,
  listTotpDevices,
  deleteTotpDevice,
};
