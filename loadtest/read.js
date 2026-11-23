// Read-heavy mix: 70% GET /entries/{id}, 30% GET /entries?page=N&size=20.
// Seeds USERS users with ENTRIES entries each, waits for the stats consumer to catch up, then runs a
// warm-up followed by a measured window at a fixed number of virtual users.
//
//   docker run --rm --network journal-app_default -v <repo>/loadtest:/scripts -v <repo>/docs/benchmarks/raw:/out \
//     -e VUS=50 grafana/k6 run --summary-export=/out/read-cache-on-vus50-run1.json /scripts/read.js
import http from 'k6/http';
import { check } from 'k6';
import { API, authHeaders, createUsers, waitForStats } from './common.js';

const VUS = parseInt(__ENV.VUS || '50', 10);
const USERS = parseInt(__ENV.USERS || '20', 10);
const ENTRIES = parseInt(__ENV.ENTRIES || '50', 10);
const WARMUP = __ENV.WARMUP || '20s';
const MEASURE = __ENV.MEASURE || '60s';

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: { executor: 'constant-vus', vus: VUS, duration: WARMUP, exec: 'mix' },
    measure: { executor: 'constant-vus', vus: VUS, duration: MEASURE, startTime: WARMUP, exec: 'mix' },
  },
  thresholds: {
    // Sub-metrics for the measured window only; the warm-up is excluded from every reported number.
    'http_reqs{scenario:measure}': ['count>=0'],
    'http_req_failed{scenario:measure}': ['rate<0.01'],
    'http_req_duration{scenario:measure}': ['max>=0'],
    'http_req_duration{scenario:measure,ep:get_entry}': ['max>=0'],
    'http_req_duration{scenario:measure,ep:list_entries}': ['max>=0'],
    'checks{scenario:measure}': ['rate>=0'],
  },
};

export function setup() {
  const users = createUsers(USERS, 'rd');
  for (const user of users) {
    const requests = [];
    for (let i = 0; i < ENTRIES; i++) {
      requests.push(['POST', `${API}/entries`,
        JSON.stringify({ title: `Entry ${i}`, content: `Seed content number ${i} for load testing. `.repeat(8) }),
        { headers: authHeaders(user.token), tags: { ep: 'seed' } }]);
    }
    const responses = http.batch(requests);
    user.ids = responses.map((r) => {
      if (r.status !== 201) {
        throw new Error(`seed failed: ${r.status} ${r.body}`);
      }
      return r.json('id');
    });
  }
  const drained = waitForStats(users, USERS * ENTRIES, 120);
  if (!drained.ok) {
    throw new Error(`stats consumer did not catch up: ${drained.sum} of ${USERS * ENTRIES}`);
  }
  return { users };
}

export function mix(data) {
  const user = data.users[Math.floor(Math.random() * data.users.length)];
  const params = { headers: authHeaders(user.token) };
  if (Math.random() < 0.7) {
    const id = user.ids[Math.floor(Math.random() * user.ids.length)];
    const res = http.get(`${API}/entries/${id}`, { ...params, tags: { ep: 'get_entry' } });
    check(res, { 'get 200': (r) => r.status === 200 });
  } else {
    const page = Math.floor(Math.random() * 3);
    const res = http.get(`${API}/entries?page=${page}&size=20`, { ...params, tags: { ep: 'list_entries' } });
    check(res, { 'list 200': (r) => r.status === 200 });
  }
}
