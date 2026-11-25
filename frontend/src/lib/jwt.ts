import type { AuthUser } from './types';

/**
 * Decodes (does NOT verify) a JWT payload in the browser for display/routing only. The backend verifies
 * signatures; the frontend never trusts this for authorization. Uses atob (browser), not Node Buffer.
 */
export function decodeJwt(token: string): AuthUser | null {
  try {
    const part = token.split('.')[1];
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/').padEnd(part.length + ((4 - (part.length % 4)) % 4), '=');
    const json = decodeURIComponent(
      atob(b64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    const claims = JSON.parse(json) as { sub?: string; tenantId?: string; roles?: string[]; exp?: number };
    if (!claims.sub || !claims.tenantId || typeof claims.exp !== 'number' || claims.exp * 1000 <= Date.now()) {
      return null;
    }
    return { userId: claims.sub, tenantId: claims.tenantId, roles: claims.roles ?? [] };
  } catch {
    return null;
  }
}
