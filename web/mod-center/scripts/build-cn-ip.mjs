// Refreshes the mainland China IP ranges from the APNIC delegation file.
// Hong Kong, Macao and Taiwan are separate registries and stay excluded.
import { writeFileSync } from 'node:fs';
import { isIP } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const source = 'https://ftp.apnic.net/apnic/stats/apnic/delegated-apnic-latest';
const output = path.join(path.dirname(fileURLToPath(import.meta.url)), '../server/cn-mainland.txt');

const ipv4ToInt = value => {
  const parts = value.split('.');
  if (parts.length !== 4 || parts.some(part => !/^\d{1,3}$/.test(part))) return null;
  let result = 0;
  for (const part of parts) {
    const number = Number(part);
    if (number > 255) return null;
    result = result * 256 + number;
  }
  return result;
};

const ipv6ToBigInt = value => {
  const halves = value.toLowerCase().split('::');
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

const merge = (ranges, one) => {
  ranges.sort((left, right) => left[0] < right[0] ? -1 : left[0] > right[0] ? 1 : 0);
  const merged = [];
  for (const range of ranges) {
    const last = merged.at(-1);
    if (!last || range[0] > last[1] + one) merged.push([...range]);
    else if (range[1] > last[1]) last[1] = range[1];
  }
  return merged;
};

const text = await (await fetch(source)).text();
const version = text.match(/^2\|apnic\|(\d+)\|/)?.[1] || 'unknown';
const ipv4 = [];
const ipv6 = [];
for (const line of text.split('\n')) {
  if (!line || line.startsWith('#')) continue;
  const parts = line.split('|');
  if (parts[1] !== 'CN' || !['allocated', 'assigned'].includes(parts[6])) continue;
  if (parts[2] === 'ipv4') {
    const start = ipv4ToInt(parts[3]);
    const count = Number(parts[4]);
    if (start === null || !Number.isSafeInteger(count) || count < 1 || start + count > 2 ** 32) continue;
    ipv4.push([start, start + count - 1]);
  } else if (parts[2] === 'ipv6' && isIP(parts[3]) === 6) {
    const start = ipv6ToBigInt(parts[3]);
    const prefix = Number(parts[4]);
    if (start === null || !Number.isInteger(prefix) || prefix < 0 || prefix > 128) continue;
    const size = 1n << BigInt(128 - prefix);
    ipv6.push([start, start + size - 1n]);
  }
}
const v4 = merge(ipv4, 1);
const v6 = merge(ipv6, 1n);
const body = [
  `# Mainland China allocations from ${source}`,
  `# APNIC serial ${version}; excludes HK, MO and TW`,
  ...v4.map(([start, end]) => `4 ${start} ${end}`),
  ...v6.map(([start, end]) => `6 ${start} ${end}`),
  '',
];
writeFileSync(output, body.join('\n'));
console.log(`Wrote ${v4.length} IPv4 and ${v6.length} IPv6 ranges to ${output}`);
