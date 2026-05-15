import api from './api';

async function updateProfile({ firstName, lastName, email }) {
  const response = await api.put('/users/me', { firstName, lastName, email });
  return response.data;
}

export default {
  updateProfile,
};
