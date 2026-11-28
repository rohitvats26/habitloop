import { describe, expect, it, vi } from 'vitest';
import { fetchAllPages } from './pagination';

describe('fetchAllPages', () => {
  it('loads every page, including items after the first hundred', async () => {
    const load = vi.fn(async (page: number) => ({
      content: page === 0 ? Array.from({ length: 100 }, (_, index) => index) : [100, 101],
      page,
      size: 100,
      totalElements: 102,
      totalPages: 2
    }));
    expect(await fetchAllPages(load)).toHaveLength(102);
    expect(load).toHaveBeenCalledTimes(2);
  });

  it('surfaces a later page failure instead of returning partial results', async () => {
    const load = vi.fn(async (page: number) => {
      if (page === 1) throw new Error('offline');
      return { content: [1], page, size: 1, totalElements: 2, totalPages: 2 };
    });
    await expect(fetchAllPages(load)).rejects.toThrow('offline');
  });

  it('accepts a genuinely empty result and rejects an unexpectedly empty page', async () => {
    await expect(fetchAllPages(async () => ({
      content: [], page: 0, size: 100, totalElements: 0, totalPages: 0
    }))).resolves.toEqual([]);
    await expect(fetchAllPages(async () => ({
      content: [], page: 0, size: 100, totalElements: 1, totalPages: 1
    }))).rejects.toThrow('invalid pagination');
  });

  it('rejects invalid pagination metadata', async () => {
    await expect(fetchAllPages(async () => ({
      content: [1], page: 1, size: 1, totalElements: 1, totalPages: 1
    }))).rejects.toThrow('invalid pagination');
  });
});
