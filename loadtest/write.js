// Write mix: each virtual user owns one account and loops create -> update, deleting every 5th entry,
// so created, updated and deleted events flow through Kafka to the stats consumer.
// After the load stops, teardown compares the stats endpoint with the entries actually stored (the entry
// list total is written synchronously, so it is the ground truth) and times how long the consumer needs
// to catch up. Counters give the number of events the script triggered.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import { API, authHeaders, createUsers, entryTotalOf, relogin, waitForStats } from './common.js';

const VUS = parseInt(__ENV.VUS || '20', 10);
const WARMUP = __ENV.WARMUP || '20s';
const MEASURE = __ENV.MEASURE || '60s';

const created = new Counter('events_created');
const updated = new Counter('events_updated');
const deleted = new Counter('events_deleted');
const storedTotal = new Gauge('verify_entries_stored');
const statsTotal = new Gauge('verify_stats_entry_count');
const drainSeconds = new Gauge('verify_drain_seconds');
const verifyOk = new Gauge('verify_ok');

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  teardownTimeout: '1500s',
  scenarios: {
    warmup: { executor: 'constant-vus', vus: VUS, duration: WARMUP, exec: 'writes' },
    measure: { executor: 'constant-vus', vus: VUS, duration: MEASURE, startTime: WARMUP, exec: 'writes' },
  },
  thresholds: {
    'http_reqs{scenario:measure}': ['count>=0'],
    'http_req_failed{scenario:measure}': ['rate<0.01'],
    'http_req_duration{scenario:measure}': ['max>=0'],
    'events_created{scenario:measure}': ['count>=0'],
    'events_updated{scenario:measure}': ['count>=0'],
    'events_deleted{scenario:measure}': ['count>=0'],
    'events_created': ['count>=0'],
    'events_updated': ['count>=0'],
    'events_deleted': ['count>=0'],
  },
};

export function setup() {
  return { users: createUsers(VUS, 'wr') };
}

let iteration = 0;

export function writes(data) {
  const user = data.users[(__VU - 1) % data.users.length];
  const params = { headers: authHeaders(user.token) };
  iteration += 1;

  const create = http.post(`${API}/entries`,
    JSON.stringify({ title: `Write ${__VU}-${iteration}`, content: 'Write test content. '.repeat(10) }),
    { ...params, tags: { ep: 'create' } });
  if (!check(create, { 'create 201': (r) => r.status === 201 })) {
    return;
  }
  created.add(1);
  const id = create.json('id');

  const update = http.put(`${API}/entries/${id}`,
    JSON.stringify({ title: `Edited ${__VU}-${iteration}`, content: 'Edited content. '.repeat(10) }),
    { ...params, tags: { ep: 'update', name: 'PUT /entries/{id}' } });
  if (check(update, { 'update 200': (r) => r.status === 200 })) {
    updated.add(1);
  }

  if (iteration % 5 === 0) {
    const del = http.del(`${API}/entries/${id}`, null, { ...params, tags: { ep: 'delete', name: 'DELETE /entries/{id}' } });
    if (check(del, { 'delete 204': (r) => r.status === 204 })) {
      deleted.add(1);
    }
  }
}

export function teardown(data) {
  relogin(data.users);
  let stored = 0;
  for (const u of data.users) {
    stored += Math.max(0, entryTotalOf(u));
  }
  const result = waitForStats(data.users, stored, 1200);
  storedTotal.add(stored);
  statsTotal.add(result.sum);
  drainSeconds.add(result.seconds);
  verifyOk.add(result.ok && result.sum === stored ? 1 : 0);
}
