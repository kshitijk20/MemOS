/**
 * Largest upload the backend accepts, in MB. Must match the backend's MEMOS_MAX_UPLOAD_SIZE;
 * set VITE_MAX_UPLOAD_MB at build time when deploying with a lower limit (e.g. small free hosts).
 */
export const MAX_UPLOAD_MB = Number(import.meta.env.VITE_MAX_UPLOAD_MB) || 50

export const MAX_FILE_SIZE_BYTES = MAX_UPLOAD_MB * 1024 * 1024
