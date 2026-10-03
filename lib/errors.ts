/**
 * An error as one readable line. Node's fetch throws a bare "fetch failed" and
 * hides the reason (DNS, refused, timeout, TLS) in `cause` — surface it, or the
 * phone shows "fetch failed" and nobody can tell which upstream broke.
 */
export function describe(error: unknown): string {
  if (!(error instanceof Error)) return String(error);
  const cause = (error as { cause?: { code?: string; message?: string; hostname?: string } }).cause;
  if (!cause) return error.message;
  const detail = [cause.code, cause.hostname].filter(Boolean).join(" ") || cause.message;
  return detail ? `${error.message} (${detail})` : error.message;
}
