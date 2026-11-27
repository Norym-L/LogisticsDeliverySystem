/// <reference types="vite/client" />

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    shell?: 'none' | 'path' | 'station' | 'courier' | 'user'
    portal?: 'path' | 'station' | 'courier'
    role?: 'PATH_ADMIN' | 'STATION_ADMIN' | 'POSTMAN'
  }
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
