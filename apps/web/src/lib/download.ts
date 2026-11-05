// Hand the viewer a JSON file. Export file names carry the UTC time (a cut-off).
export function stamp(now = new Date()): string {
  return now.toISOString().slice(0, 16).replace(/[-:]/g, '').replace('T', '-') + 'Z';
}

export function saveJson(name: string, data: unknown): void {
  const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
  const a = Object.assign(document.createElement('a'), { href: url, download: name });
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
