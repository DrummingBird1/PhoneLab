import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  server: {
    port: 5183,
    strictPort: true,
  },
  plugins: [
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg"],
      workbox: {
        // Precache every built JS chunk, including the lazily imported he/es/ar dictionaries
        // (src/i18n.ts), so the app — and switching language — keeps working offline.
        globPatterns: ["**/*.{js,css,html}"],
      },
      manifest: {
        name: "SensoLab Web",
        short_name: "SensoLab",
        description: "Every sensor your browser will give up — live, in one dashboard.",
        start_url: "/",
        display: "standalone",
        background_color: "#0b0f1a",
        theme_color: "#0b0f1a",
        icons: [
          { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "/icons/icon-maskable-192.png", sizes: "192x192", type: "image/png", purpose: "maskable" },
          { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
    }),
  ],
});
