/** Extrae un mensaje legible de errores HTTP del backend (detail string u objeto). */
export function apiErrorMessage(error: unknown, fallback: string): string {
  const body = (error as { error?: unknown } | null)?.error;
  if (typeof body === 'string' && body.trim()) return body.trim();

  const detail = (body as { detail?: unknown } | null)?.detail;
  if (typeof detail === 'string' && detail.trim()) return detail.trim();

  if (detail && typeof detail === 'object') {
    const nested = detail as Record<string, unknown>;
    const nestedDetail = nested['detail'];
    if (typeof nestedDetail === 'string' && nestedDetail.trim()) return nestedDetail.trim();
    const message = nested['message'] ?? nested['mensaje'];
    if (typeof message === 'string' && message.trim()) return message.trim();
  }

  const message = (error as { message?: unknown } | null)?.message;
  if (typeof message === 'string' && message.trim() && message !== '[object Object]') {
    return message.trim();
  }

  return fallback;
}

/** Lee ids de sesión abierta cuando el backend responde conflict con detail objeto. */
export function apiConflictSession(error: unknown): {
  idCaja: number | null;
  idCajaSesion: number | null;
} {
  const detail = (error as { error?: { detail?: unknown } } | null)?.error?.detail;
  if (!detail || typeof detail !== 'object') {
    return { idCaja: null, idCajaSesion: null };
  }
  const nested = detail as Record<string, unknown>;
  const idCaja = Number(nested['id_caja']);
  const idCajaSesion = Number(nested['id_caja_sesion']);
  return {
    idCaja: Number.isFinite(idCaja) ? idCaja : null,
    idCajaSesion: Number.isFinite(idCajaSesion) ? idCajaSesion : null,
  };
}
