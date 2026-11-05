// Seeded PRNG (mulberry32): the same seed gives the same data on every run.
export type Rand = {
  next(): number;
  int(min: number, max: number): number;
  pick<T>(items: readonly T[]): T;
  chance(p: number): boolean;
};

export function seeded(seed: number): Rand {
  let a = seed >>> 0;
  const next = (): number => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const int = (min: number, max: number) => min + Math.floor(next() * (max - min + 1));
  return { next, int, pick: (items) => items[int(0, items.length - 1)], chance: (p) => next() < p };
}

const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

/** A ULID for `time`: 10 time chars + 16 random chars, so ids sort by creation. */
export function ulid(time: number, rand: Rand): string {
  let t = time;
  let head = '';
  for (let i = 0; i < 10; i++) {
    head = CROCKFORD[t % 32] + head;
    t = Math.floor(t / 32);
  }
  let tail = '';
  for (let i = 0; i < 16; i++) tail += CROCKFORD[rand.int(0, 31)];
  return head + tail;
}

export const hex = (rand: Rand, length: number): string =>
  Array.from({ length }, () => rand.int(0, 15).toString(16)).join('');
