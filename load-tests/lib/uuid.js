import crypto from 'k6/crypto';

const HEX = '0123456789abcdef';

/**
 * RFC 4122 version 4 UUID built from k6's CSPRNG.
 * The service persists UUIDs in native Postgres `uuid` columns, so the value
 * only needs to be unique and correctly formatted.
 */
export function uuid() {
  const bytes = new Uint8Array(crypto.randomBytes(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  let out = '';
  for (let i = 0; i < 16; i++) {
    if (i === 4 || i === 6 || i === 8 || i === 10) out += '-';
    out += HEX[(bytes[i] >> 4) & 0x0f] + HEX[bytes[i] & 0x0f];
  }
  return out;
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function pickIndex(length) {
  return Math.floor(Math.random() * length);
}

/** Two decimals, matching the NUMERIC(19, 2) columns. */
export function money(min, max) {
  const cents = Math.floor(Math.random() * ((max - min + 1) * 100)) + min * 100;
  return cents / 100;
}

export function today() {
  return new Date().toISOString().slice(0, 10);
}