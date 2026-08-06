/** Formats server timestamps in India Standard Time (IST).
 * A timestamp without an offset is treated as an IST local timestamp,
 * which is how the Spring services serialize LocalDateTime values.
 */
export function parseIndiaTimestamp(value: any): Date | null {
  if (!value) return null;
  const raw = String(value).trim();
  if (!raw) return null;
  const hasOffset = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw);
  const parsed = new Date(hasOffset ? raw : `${raw}+05:30`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function formatIndiaDateTime(value: any): string {
  const date = parseIndiaTimestamp(value);
  if (!date) return '-';
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).format(date);
}