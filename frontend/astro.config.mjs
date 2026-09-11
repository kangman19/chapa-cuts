// @ts-check
import { defineConfig } from 'astro/config';

// Builds straight into the Spring Boot static folder so the whole thing ships as one app on one port.
export default defineConfig({
  outDir: '../backend/src/main/resources/static',
  build: { format: 'file' },
  server: {
    port: 4321,
  },
  vite: {
    server: {
      // `astro dev` proxies API calls to the Spring Boot app so you can work on the pages with hot reload.
      proxy: { '/api': 'http://localhost:8080' },
    },
  },
});
