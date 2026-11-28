import type { Page } from './types';

export async function fetchAllPages<T>(load: (page: number) => Promise<Page<T>>): Promise<T[]> {
  const result: T[] = [];
  let pageNumber = 0;
  let totalPages: number;
  do {
    const page = await load(pageNumber);
    if (!Array.isArray(page.content) || page.page !== pageNumber
        || !Number.isInteger(page.size) || page.size < 1
        || !Number.isInteger(page.totalElements) || page.totalElements < 0
        || !Number.isInteger(page.totalPages) || page.totalPages < 0
        || page.totalPages !== Math.ceil(page.totalElements / page.size)
        || page.content.length > page.size
        || (pageNumber < page.totalPages && page.content.length === 0)
        || (page.totalPages === 0 && page.content.length > 0)) {
      throw new Error('The service returned invalid pagination data.');
    }
    result.push(...page.content);
    totalPages = page.totalPages;
    pageNumber++;
  } while (pageNumber < totalPages);
  return result;
}
