// Static SPA: render everything on the client (no SSR), and don't prerender route pages —
// the adapter's index.html fallback boots the app for any path (works on Amazon S3).
export const ssr = false;
export const prerender = false;
