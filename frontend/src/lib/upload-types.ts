/**
 * File types the backend can ingest: documents, plus photos whose text is read with OCR.
 * `image/*` also lets phones offer the camera, so notes can be photographed straight in.
 */
export const UPLOAD_ACCEPT = '.pdf,.md,.markdown,.txt,.jpg,.jpeg,.png,.webp,.heic,.heif,image/*'

const SUPPORTED_EXTENSIONS = ['pdf', 'md', 'markdown', 'txt', 'jpg', 'jpeg', 'png', 'webp', 'heic', 'heif']

export function isSupportedUpload(filename: string): boolean {
  const extension = filename.split('.').pop()?.toLowerCase() ?? ''
  return SUPPORTED_EXTENSIONS.includes(extension)
}
