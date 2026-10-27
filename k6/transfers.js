import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: { http_req_failed: ['rate<0.05'], http_req_duration: ['p(95)<1500'] },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const token = __ENV.ACCESS_TOKEN;

export default function () {
  if (!token) throw new Error('Set ACCESS_TOKEN to a valid demo access token.');
  const response = http.get(`${baseUrl}/api/v1/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  check(response, { 'account list responds successfully': (r) => r.status === 200 });
  sleep(1);
}
