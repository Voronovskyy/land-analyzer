import axios from 'axios';

const BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8080') + '/api';

export const searchPlot = (query) =>
  axios.post(`${BASE}/search`, { query }).then(r => r.data);

export const getMapHtml = (data) =>
  axios.post(`${BASE}/map`, data, { responseType: 'text' }).then(r => r.data);

export const generateReport = async (request) => {
  const response = await axios.post(`${BASE}/report`, request, {
    responseType: 'blob',
    timeout: 120000,
  });
  const url = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `LandReport_${Date.now()}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
};
