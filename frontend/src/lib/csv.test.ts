import { describe, expect, it } from 'vitest';
import { toCsv } from './csv';

describe('toCsv', () => {
  it('quotes values, escapes embedded quotes, and retains row boundaries', () => {
    expect(toCsv(['Name', 'Note'], [['Morning walk', 'felt "great"\ntoday']])).toBe(
      '"Name","Note"\r\n"Morning walk","felt ""great""\ntoday"'
    );
  });

  it('neutralizes spreadsheet formula prefixes', () => {
    expect(toCsv(['Value'], [['=1+1'], ['  @SUM(A1:A2)']])).toBe(
      '"Value"\r\n"\'=1+1"\r\n"\'  @SUM(A1:A2)"'
    );
  });
});
