export interface IngestionErrorView {
  label: string
  title: string
  guidance: string
}

export function explainIngestionError(raw: string): IngestionErrorView {
  const text = raw.trim()
  const lower = text.toLowerCase()

  if (lower.includes('status=403') || lower.includes('http 403') || lower.includes('access denied')) {
    return {
      label: 'Site blocked this page (403)',
      title: 'This site blocked the fetch',
      guidance:
        'Medium and similar pages often reject automated requests. Save the article and upload it as a file, or ingest a different URL.',
    }
  }
  if (lower.includes('http 401') || lower.includes('status=401')) {
    return {
      label: 'Login required (401)',
      title: 'This page requires a login',
      guidance: 'Save the page while signed in and upload the file instead of fetching the URL.',
    }
  }
  if (
    lower.includes('429') ||
    lower.includes('exceeded your current quota') ||
    lower.includes('resource_exhausted') ||
    lower.includes('rate-limited') ||
    lower.includes('quota exceeded') ||
    lower.includes('embedding quota')
  ) {
    return {
      label: 'Embedding quota exceeded',
      title: 'Embedding quota exceeded',
      guidance: 'Wait and retry later, or check your AI plan and billing.',
    }
  }
  if (lower.includes('timed out') || lower.includes('timeout')) {
    return {
      label: 'Request timed out',
      title: 'The fetch timed out',
      guidance: 'The site was too slow or blocked the request. Retry, or upload a saved copy of the page.',
    }
  }
  if (lower.includes('cloudflare') || lower.includes('bot protection')) {
    return {
      label: 'Blocked by bot protection',
      title: 'Blocked by Cloudflare',
      guidance: 'Retry in a moment, or save the page and upload it as a file.',
    }
  }
  if (lower.includes('no readable text')) {
    return {
      label: 'No readable text on page',
      title: 'No readable text found',
      guidance: 'The page may be empty, JavaScript-only, or a challenge screen. Upload a saved copy instead.',
    }
  }
  if (lower.includes('page not found') || lower.includes('status=404') || lower.includes('http 404')) {
    return {
      label: 'Page not found (404)',
      title: 'Page not found',
      guidance: 'Check the URL and try again.',
    }
  }
  if (lower.includes('original upload is no longer on disk')) {
    return {
      label: 'Original file is gone',
      title: 'Original upload is gone',
      guidance: 'Re-ingest this document and upload the file again.',
    }
  }
  if (lower.includes('interrupted because the application restarted')) {
    return {
      label: 'Interrupted by restart',
      title: 'Ingestion was interrupted',
      guidance: 'The app restarted mid-ingest. Retry to continue.',
    }
  }

  return {
    label: 'Ingestion failed',
    title: 'Ingestion failed',
    guidance: 'Retry this document. If it fails again, check the technical details.',
  }
}
