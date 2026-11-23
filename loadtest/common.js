import http from 'k6/http';
import { check, sleep } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://app:8080';
export const API = `${BASE}/api/v1`;
export const JSON_HEADERS = { 'Content-Type': 'application/json' };
// Load-test accounts only. They are created fresh on every run with a unique suffix.
const PASSWORD = 'loadtest-password-1';

export function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

/** Registers and logs in `count` users and returns [{username, token}]. */
export function createUsers(count, prefix) {
  const suffix = `${Date.now()}`;
  const users = [];
  for (let i = 0; i < count; i++) {
    const username = `${prefix}-${suffix}-${i}`;
    const reg = http.post(`${API}/auth/register`,
      JSON.stringify({ username, password: PASSWORD, displayName: username }), { headers: JSON_HEADERS });
    if (reg.status !== 201 && reg.status !== 200) {
      throw new Error(`register failed: ${reg.status} ${reg.body}`);
    }
    const login = http.post(`${API}/auth/login`,
      JSON.stringify({ username, password: PASSWORD }), { headers: JSON_HEADERS });
    if (login.status !== 200) {
      throw new Error(`login failed: ${login.status} ${login.body}`);
    }
    users.push({ username, token: login.json('accessToken') });
  }
  return users;
}

/** Access tokens last 15 minutes; a long drain wait logs the users in again before they expire. */
export function relogin(users) {
  for (const user of users) {
    const login = http.post(`${API}/auth/login`,
      JSON.stringify({ username: user.username, password: PASSWORD }), { headers: JSON_HEADERS });
    if (login.status === 200) {
      user.token = login.json('accessToken');
    }
  }
}

export function statsOf(user) {
  const res = http.get(`${API}/users/me/stats`, { headers: authHeaders(user.token), tags: { ep: 'setup' } });
  return res.status === 200 ? res.json('entryCount') : -1;
}

export function entryTotalOf(user) {
  const res = http.get(`${API}/entries?size=1`, { headers: authHeaders(user.token), tags: { ep: 'setup' } });
  return res.status === 200 ? res.json('totalElements') : -1;
}

/** Polls until the sum of stats entry counts equals `expected` or `timeoutSec` passes. Returns {ok, sum, seconds}. */
export function waitForStats(users, expected, timeoutSec) {
  const start = Date.now();
  let sum = -1;
  let lastLogin = Date.now();
  while ((Date.now() - start) / 1000 < timeoutSec) {
    if (Date.now() - lastLogin > 600000) {
      relogin(users);
      lastLogin = Date.now();
    }
    sum = 0;
    for (const u of users) {
      sum += Math.max(0, statsOf(u));
    }
    if (sum === expected) {
      return { ok: true, sum, seconds: (Date.now() - start) / 1000 };
    }
    sleep(0.25);
  }
  return { ok: false, sum, seconds: (Date.now() - start) / 1000 };
}

export { check };
