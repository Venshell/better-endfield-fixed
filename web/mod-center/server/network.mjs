import { readFileSync } from 'node:fs';
import { isIP } from 'node:net';

const compareNumber = (left, right) => left - right;
const compareBigInt = (left, right) => left < right ? -1 : left > right ? 1 : 0;

export const normalizeIp = value => {
  if (typeof value !== 'string') return '';
  let ip = value.trim().toLowerCase();
  if (ip.startsWith('[') && ip.endsWith(']')) ip = ip.slice(1, -1);
  if (ip.startsWith('::ffff:')) ip = ip.slice('::ffff:'.length);
  const zone = ip.indexOf('%');
  if (zone !== -1) ip = ip.slice(0, zone);
  return isIP(ip) ? ip : '';
};

// The public HTTPS proxy runs on this machine and replaces X-Forwarded-For
// with the address it actually accepted. A direct caller cannot supply that header.
export const clientAddress = req => {
  const peer = normalizeIp(req.socket?.remoteAddress || '');
  const header = String(req.headers?.['x-forwarded-for'] || '').trim();
  if ((peer === '127.0.0.1' || peer === '::1') && header && !/[\s,]/.test(header)) {
    const forwarded = normalizeIp(header);
    if (forwarded) return forwarded;
  }
  return peer;
};

const ipv4ToInt = value => {
  const parts = value.split('.');
  if (parts.length !== 4) return null;
  let result = 0;
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) return null;
    const number = Number(part);
    if (number > 255) return null;
    result = result * 256 + number;
  }
  return result;
};

const ipv6ToBigInt = value => {
  const halves = value.split('::');
  if (halves.length > 2) return null;
  const groupsOf = part => {
    if (!part) return [];
    const groups = part.split(':');
    if (groups.some(group => !/^[0-9a-f]{1,4}$/.test(group))) return null;
    return groups.map(group => Number.parseInt(group, 16));
  };
  const head = groupsOf(halves[0]);
  if (!head) return null;
  let groups = head;
  if (halves.length === 2) {
    const tail = groupsOf(halves[1]);
    if (!tail || head.length + tail.length > 8) return null;
    groups = [...head, ...Array(8 - head.length - tail.length).fill(0), ...tail];
  }
  if (groups.length !== 8) return null;
  return groups.reduce((result, group) => (result << 16n) + BigInt(group), 0n);
};

const loadRanges = () => {
  const text = readFileSync(new URL('./cn-mainland.txt', import.meta.url), 'utf8');
  const ipv4 = [];
  const ipv6 = [];
  for (const line of text.split('\n')) {
    if (!line || line.startsWith('#')) continue;
    const [kind, start, end] = line.split(' ');
    if (kind === '4') ipv4.push([Number(start), Number(end)]);
    else if (kind === '6') ipv6.push([BigInt(start), BigInt(end)]);
    else throw new Error('Invalid mainland IP range');
    const range = kind === '4' ? ipv4.at(-1) : ipv6.at(-1);
    if (range[0] > range[1]) throw new Error('Invalid mainland IP range');
  }
  if (!ipv4.length || !ipv6.length) throw new Error('Mainland IP ranges are missing');
  return { ipv4, ipv6 };
};

const ranges = loadRanges();

const contains = (items, value, compare) => {
  let low = 0;
  let high = items.length - 1;
  let found = -1;
  while (low <= high) {
    const mid = (low + high) >> 1;
    if (compare(items[mid][0], value) <= 0) { found = mid; low = mid + 1; }
    else high = mid - 1;
  }
  return found >= 0 && compare(value, items[found][1]) <= 0;
};

export const isMainlandChina = value => {
  const ip = normalizeIp(value);
  if (isIP(ip) === 4) return contains(ranges.ipv4, ipv4ToInt(ip), compareNumber);
  if (isIP(ip) === 6) {
    const parsed = ipv6ToBigInt(ip);
    return parsed !== null && contains(ranges.ipv6, parsed, compareBigInt);
  }
  return false;
};
