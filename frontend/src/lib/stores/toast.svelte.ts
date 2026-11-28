export interface Toast {
  id: number;
  type: 'info' | 'success' | 'error';
  message: string;
}

export const toasts = $state<Toast[]>([]);
let seq = 0;

export function pushToast(message: string, type: Toast['type'] = 'info') {
  const id = ++seq;
  toasts.push({ id, type, message });
  // Errors stay until dismissed so they can be read at any pace.
  if (type !== 'error') setTimeout(() => dismissToast(id), 5000);
}

export function dismissToast(id: number) {
  const i = toasts.findIndex((t) => t.id === id);
  if (i >= 0) toasts.splice(i, 1);
}
