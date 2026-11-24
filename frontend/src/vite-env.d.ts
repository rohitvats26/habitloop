/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_USER_SERVICE_URL?: string;
  readonly VITE_ACTIVITY_SERVICE_URL?: string;
  readonly VITE_CHALLENGE_SERVICE_URL?: string;
  readonly VITE_INTEGRATION_SERVICE_URL?: string;
  readonly VITE_ANALYTICS_SERVICE_URL?: string;
  readonly VITE_RECOMMENDATION_SERVICE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
