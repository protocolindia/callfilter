import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    allowedHosts: true
  },
  preview: {
    port: parseInt(process.env.PORT || '4173', 10),
    host: '0.0.0.0',
    // Allow any host — required for Railway-generated subdomains.
    // `true` disables the host check entirely; safe here because the
    // service is behind Railway's edge and the only thing it serves
    // is static built assets.
    allowedHosts: true
  }
});
