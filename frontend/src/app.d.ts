import type { AuthUser } from '$lib/types';

declare global {
  namespace App {
    interface Locals {
      token?: string;
      user?: AuthUser;
    }
    interface PageData {
      user?: AuthUser;
    }
  }
}

export {};
