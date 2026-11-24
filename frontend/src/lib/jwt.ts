import type { AuthUser } from '$lib/types';

/**
 * Decodes (does NOT verify) a JWT payload for display/routing only. The backend verifies signatures;
 * the frontend never trusts this for authorization.
 */
export function decodeJwt(token: string): AuthUser | null {
  try {
    const part = token.split('.')[1];
    const json = Buffer.from(part, 'base64url').toString('utf-8');
    const claims = JSON.parse(json) as { sub: string; tenantId: string; roles?: string[]; exp?: number };
    if (claims.exp && claims.exp * 1000 < Date.now()) return null;
    return { userId: claims.sub, tenantId: claims.tenantId, roles: claims.roles ?? [] };
  } catch {
    return null;
  }
}
