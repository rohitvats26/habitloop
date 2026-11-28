import { browser } from '$app/environment';

export type ThemePreference = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'hl_theme';

function readPreference(): ThemePreference {
  if (!browser) return 'system';
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

export const theme = $state<{ preference: ThemePreference }>({ preference: readPreference() });

export function setTheme(preference: ThemePreference) {
  theme.preference = preference;
  if (!browser) return;
  const root = document.documentElement;
  if (preference === 'system') delete root.dataset.theme;
  else root.dataset.theme = preference;
  try {
    if (preference === 'system') localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, preference);
  } catch {
    /* storage unavailable — theme applies for this page only */
  }
}
