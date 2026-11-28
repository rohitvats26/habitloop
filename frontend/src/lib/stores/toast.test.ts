import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { pushToast, toasts } from './toast.svelte';

beforeEach(() => {
  vi.useFakeTimers();
  toasts.splice(0, toasts.length);
});

afterEach(() => {
  vi.useRealTimers();
});

describe('pushToast', () => {
  it('auto-dismisses confirmations but keeps errors until the user dismisses them', () => {
    pushToast('Saved', 'success');
    pushToast('Could not save', 'error');
    vi.advanceTimersByTime(60_000);
    expect(toasts.map((toast) => toast.message)).toEqual(['Could not save']);
  });
});
