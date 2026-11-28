import { beforeNavigate } from '$app/navigation';
import { onMount } from 'svelte';

export const UNSAVED_CHANGES_MESSAGE = 'You have unsaved changes. Leave this page and discard them?';

/** Must be called during component initialisation. Warns before in-app navigation, reloads, and tab closes. */
export function guardUnsavedChanges(isDirty: () => boolean) {
  beforeNavigate((navigation) => {
    if (navigation.type === 'leave' || !isDirty()) return;
    if (!window.confirm(UNSAVED_CHANGES_MESSAGE)) navigation.cancel();
  });

  onMount(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!isDirty()) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  });
}
