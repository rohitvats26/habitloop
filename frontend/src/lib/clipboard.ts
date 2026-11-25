export async function copyToClipboard(value: string): Promise<void> {
  if (!navigator.clipboard?.writeText) {
    throw new Error('Clipboard access is unavailable. Copy the value manually.');
  }
  await navigator.clipboard.writeText(value);
}
