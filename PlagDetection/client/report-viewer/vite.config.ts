import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import type { UserConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite';
import {PrimeVueResolver} from '@primevue/auto-import-resolver';

// https://vitejs.dev/config/
export default defineConfig((userConfig: UserConfig) => {
  let base = '/'
  switch (userConfig.mode) {
    case 'dev':
      base = '/JPlag-Dev/'
      break
    case 'prod':
      base = '/JPlag/'
      break
    case 'demo':
      base = '/Demo/'
      break
  }
  return {
    plugins: [
      vue(),
      Components({
        resolvers: [
          PrimeVueResolver()
        ]
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    base: base
  }
})
