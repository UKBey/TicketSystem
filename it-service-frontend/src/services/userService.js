import api from './api';

async function updateProfile({ firstName, lastName, email }) {
  const response = await api.put('/users/me', { firstName, lastName, email });
  return response.data;
}

async function changePassword({ currentPassword, newPassword }) {
  await api.post('/users/me/password', { currentPassword, newPassword });
}

export default {
  updateProfile,
  changePassword,
};
