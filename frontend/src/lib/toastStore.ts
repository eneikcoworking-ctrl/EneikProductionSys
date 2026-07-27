import { writable } from 'svelte/store';

export type ToastType = 'info' | 'success' | 'warning' | 'error';

export type ToastMessage = {
  id: string;
  type: ToastType;
  message: string;
  durationMs?: number;
};

export const toastsStore = writable<ToastMessage[]>([]);

export function addToast(type: ToastType, message: string, durationMs: number = 4500) {
  const id = Math.random().toString(36).substring(2, 9);
  const toast: ToastMessage = { id, type, message, durationMs };
  toastsStore.update(current => [...current, toast]);

  if (durationMs > 0) {
    setTimeout(() => {
      removeToast(id);
    }, durationMs);
  }
}

export function removeToast(id: string) {
  toastsStore.update(current => current.filter(t => t.id !== id));
}
