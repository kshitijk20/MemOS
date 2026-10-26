// Cloudflare Worker for the MemOS frontend.
// - Page requests are served from the built static assets (see wrangler.jsonc).
// - A cron trigger pings the backend's health endpoint so Render's free plan doesn't put it to
//   sleep (it sleeps after 15 minutes without traffic; waking takes a minute or more).
export default {
  async fetch(request, env) {
    return env.ASSETS.fetch(request)
  },

  async scheduled(event, env, ctx) {
    ctx.waitUntil(
      fetch(env.BACKEND_HEALTH_URL, { headers: { 'User-Agent': 'memos-keep-alive' } })
        .then((response) => console.log(`keep-alive: ${response.status}`))
        .catch((error) => console.error(`keep-alive failed: ${error}`)),
    )
  },
}
