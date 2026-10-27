// Service worker for the installed MemOS app. Its one job is the share target: when a link,
// text or file is shared to MemOS from another app, the OS POSTs it to /share-target. A static
// site can't receive a POST, so this worker catches it, keeps any files in a cache, and opens
// /share, where the signed-in app saves them. Nothing else is intercepted or cached.

const SHARE_CACHE = 'memos-share'

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  if (event.request.method === 'POST' && url.pathname === '/share-target') {
    event.respondWith(receiveShare(event.request))
  }
})

async function receiveShare(request) {
  const form = await request.formData()
  const files = form.getAll('files').filter((file) => file instanceof File && file.size > 0)

  const cache = await caches.open(SHARE_CACHE)
  for (const key of await cache.keys()) {
    await cache.delete(key)
  }
  await Promise.all(
    files.map((file, index) =>
      cache.put(
        `/shared-file/${index}`,
        new Response(file, {
          headers: {
            'Content-Type': file.type || 'application/octet-stream',
            'X-File-Name': encodeURIComponent(file.name),
          },
        }),
      ),
    ),
  )

  const params = new URLSearchParams()
  for (const key of ['title', 'text', 'url']) {
    const value = form.get(key)
    if (typeof value === 'string' && value.trim()) params.set(key, value.trim())
  }
  if (files.length) params.set('files', String(files.length))
  return Response.redirect(`/share?${params}`, 303)
}
