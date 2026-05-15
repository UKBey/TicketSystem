import api from './api';

async function updateProfile({ firstName, lastName, email }) {
  const response = await api.put('/users/me', { firstName, lastName, email });
  return response.data;
}

async function requestPasswordChange() {
  await api.post('/users/me/request-password-change');
}

export default {
  updateProfile,
  requestPasswordChange,
};
