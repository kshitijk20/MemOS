// Cloudflare Worker for the MemOS frontend.
// - Page requests are served from the built static assets (see wrangler.jsonc), with security
//   headers added.
// - A cron trigger pings the backend's health endpoint so Render's free plan doesn't put it to
//   sleep (it sleeps after 15 minutes without traffic; waking takes a minute or more).
export default {
  async fetch(request, env) {
    const response = await env.ASSETS.fetch(request)
    const headers = new Headers(response.headers)
    for (const [name, value] of Object.entries(securityHeaders(env))) {
      headers.set(name, value)
    }
    return new Response(response.body, { status: response.status, statusText: response.statusText, headers })
  },

  async scheduled(event, env, ctx) {
    ctx.waitUntil(
      fetch(env.BACKEND_HEALTH_URL, { headers: { 'User-Agent': 'memos-keep-alive' } })
        .then((response) => console.log(`keep-alive: ${response.status}`))
        .catch((error) => console.error(`keep-alive failed: ${error}`)),
    )
  },
}

// The Content-Security-Policy only lets the page run its own scripts and talk to its own backend,
// so injected HTML can't load a script or send the stored sign-in token anywhere else.
// Inline styles stay allowed: the UI libraries set them at runtime.
function securityHeaders(env) {
  const backendOrigin = new URL(env.BACKEND_HEALTH_URL).origin
  return {
    'Content-Security-Policy': [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "font-src 'self' data:",
      `connect-src 'self' ${backendOrigin}`,
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'",
    ].join('; '),
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
    // Reset links carry a token in the URL; never send it to other sites as a referrer.
    'Referrer-Policy': 'no-referrer',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
  }
}
