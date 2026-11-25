import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

const componentsPath = decodeURIComponent(new URL('../components-stencil', import.meta.url).pathname)
  .replace(/^\/([A-Za-z]:\/)/, '$1');

export default defineConfig({
  plugins: [sveltekit()],
  server: {
    port: 5173,
    fs: {
      allow: [componentsPath]
    }
  }
});
