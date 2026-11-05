import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

// Vietnamese glossary guard (C10, 2026-11-05): the two roles are called "Client" and "Freelancer" in every user-facing text.
// The plain-language variants ("khách", "khách hàng", "người làm") were removed so that one screen never mixes two names.
function sources(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.(ts|tsx)$/.test(name) && !/\.test\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

describe('Vietnamese role terms', () => {
  it('uses Client and Freelancer, never khách, khách hàng or người làm, in the application sources', () => {
    const offenders = sources(__dirname.replace(/\\/g, '/')).filter(file => /(?<![\wÀ-ỹ])khách(?![\wÀ-ỹ])|người làm/i.test(readFileSync(file, 'utf8')));
    expect(offenders).toEqual([]);
  });
});
