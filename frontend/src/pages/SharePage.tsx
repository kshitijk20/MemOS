import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { FileText, Globe, Image as ImageIcon, Share2, StickyNote } from 'lucide-react'
import { toast } from 'sonner'
import { ingestUrl, uploadDocument } from '@/api/documents'
import { extractErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { MAX_FILE_SIZE_BYTES, MAX_UPLOAD_MB } from '@/lib/upload-limit'
import { isSupportedUpload } from '@/lib/upload-types'

/** Must match SHARE_CACHE in public/sw.js, which stores the shared files here. */
const SHARE_CACHE = 'memos-share'

type SharedItem =
  | { kind: 'link'; title: string; url: string }
  | { kind: 'note'; title: string; text: string }
  | { kind: 'file'; title: string; file: File }

interface ItemState {
  item: SharedItem
  error?: string
}

const URL_PATTERN = /https?:\/\/[^\s<>"']+/

function titleFromUrl(url: string): string {
  try {
    const { hostname, pathname } = new URL(url)
    const path = pathname.replace(/\/$/, '')
    return `${hostname.replace(/^www\./, '')}${path}`.slice(0, 120)
  } catch {
    return url.slice(0, 120)
  }
}

function titleFromFile(name: string): string {
  return name.replace(/\.[^.]+$/, '') || name
}

/** Files the service worker stored when something was shared to the app. */
async function readSharedFiles(): Promise<File[]> {
  if (!('caches' in window)) return []
  const cache = await caches.open(SHARE_CACHE)
  const files: File[] = []
  for (const request of await cache.keys()) {
    const response = await cache.match(request)
    if (!response) continue
    const blob = await response.blob()
    const name = decodeURIComponent(response.headers.get('X-File-Name') ?? 'shared-file')
    files.push(new File([blob], name, { type: blob.type }))
  }
  return files
}

async function clearSharedFiles() {
  if ('caches' in window) await caches.delete(SHARE_CACHE)
}

/** Turns what another app shared (title, text, url, files) into things to save. */
function toItems(params: URLSearchParams, files: File[]): SharedItem[] {
  const sharedTitle = params.get('title')?.trim() ?? ''
  const text = params.get('text')?.trim() ?? ''
  // Many apps put the link inside the text rather than the url field.
  const url = params.get('url')?.trim() || text.match(URL_PATTERN)?.[0] || ''
  const items: SharedItem[] = []

  if (url) {
    items.push({ kind: 'link', url, title: sharedTitle || titleFromUrl(url) })
  }
  const noteText = url ? text.replace(url, '').trim() : text
  // Text on its own is saved as a note; text next to a link is usually just its caption.
  if (!url && files.length === 0 && noteText) {
    const firstLine = noteText.split('\n')[0].slice(0, 80)
    items.push({ kind: 'note', text: noteText, title: sharedTitle || firstLine || 'Shared note' })
  }
  for (const file of files) {
    items.push({ kind: 'file', file, title: titleFromFile(file.name) })
  }
  return items
}

function problemWith(item: SharedItem): string | undefined {
  if (item.kind !== 'file') return undefined
  if (!isSupportedUpload(item.file.name)) return 'This file type isn’t supported.'
  if (item.file.size > MAX_FILE_SIZE_BYTES) return `Larger than the ${MAX_UPLOAD_MB}MB upload limit.`
  return undefined
}

function ItemIcon({ item }: { item: SharedItem }) {
  const className = 'size-4'
  if (item.kind === 'link') return <Globe className={className} />
  if (item.kind === 'note') return <StickyNote className={className} />
  if (item.file.type.startsWith('image/')) return <ImageIcon className={className} />
  return <FileText className={className} />
}

function itemSubtitle(item: SharedItem): string {
  if (item.kind === 'link') return item.url
  if (item.kind === 'note') return `${item.text.length} characters of text`
  const kb = item.file.size / 1024
  const size = kb < 1024 ? `${Math.max(1, Math.round(kb))} KB` : `${(kb / 1024).toFixed(1)} MB`
  const ocr = item.file.type.startsWith('image/') ? ' · text is read from the photo' : ''
  return `${item.file.name} · ${size}${ocr}`
}

async function save(item: SharedItem) {
  const title = item.title.trim() || 'Untitled'
  if (item.kind === 'link') {
    await ingestUrl({ url: item.url, title })
  } else if (item.kind === 'note') {
    await uploadDocument(new File([item.text], `${title}.md`, { type: 'text/markdown' }), title)
  } else {
    await uploadDocument(item.file, title)
  }
}

export function SharePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [items, setItems] = useState<ItemState[] | null>(null)
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    let cancelled = false
    readSharedFiles()
      .catch(() => [] as File[])
      .then((files) => {
        if (!cancelled) setItems(toItems(params, files).map((item) => ({ item, error: problemWith(item) })))
      })
    return () => {
      cancelled = true
    }
  }, [params])

  function setTitle(index: number, title: string) {
    setItems((current) =>
      current?.map((state, i) => (i === index ? { ...state, item: { ...state.item, title } } : state)) ?? null,
    )
  }

  const savable = items?.filter((state) => !problemWith(state.item)) ?? []

  async function handleSave() {
    if (!items) return
    setIsSaving(true)
    const failed: ItemState[] = []
    let saved = 0
    for (const state of items) {
      if (problemWith(state.item)) continue
      try {
        await save(state.item)
        saved++
      } catch (error) {
        failed.push({ ...state, error: extractErrorMessage(error) })
      }
    }
    await queryClient.invalidateQueries({ queryKey: ['documents'] })
    setIsSaving(false)
    if (failed.length === 0) {
      await clearSharedFiles()
      toast.success(saved === 1 ? 'Saved to MemOS. It will be searchable in a moment.' : `Saved ${saved} items to MemOS.`)
      navigate('/documents', { replace: true })
    } else {
      if (saved > 0) toast.success(`Saved ${saved} of ${saved + failed.length} items.`)
      setItems(failed)
    }
  }

  async function handleDiscard() {
    await clearSharedFiles()
    navigate('/documents', { replace: true })
  }

  if (items === null) {
    return (
      <div className="mx-auto w-full max-w-xl">
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="mx-auto w-full max-w-xl">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Share2 className="size-5" />
              Save to MemOS from any app
            </CardTitle>
            <CardDescription>Nothing was shared. Here's how to send links, files and photos to MemOS.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm text-muted-foreground">
            <p>
              <span className="font-medium text-foreground">Android:</span> in Chrome, open the menu (⋮) and choose{' '}
              <span className="font-medium text-foreground">Add to Home screen</span> or{' '}
              <span className="font-medium text-foreground">Install app</span>. Then, in any app, tap{' '}
              <span className="font-medium text-foreground">Share → MemOS</span>.
            </p>
            <p>
              <span className="font-medium text-foreground">iPhone:</span> Safari doesn't let web apps appear in the share
              menu. Use <span className="font-medium text-foreground">Upload</span> on the Documents page instead; you can
              take a photo of your notes right from there.
            </p>
            <Button asChild variant="outline" className="mt-2">
              <Link to="/documents">Go to Documents</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-xl">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Share2 className="size-5" />
            Save to MemOS
          </CardTitle>
          <CardDescription>
            {items.length === 1 ? 'Check the title, then save.' : `${items.length} items shared. Check the titles, then save.`}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-5">
          {items.map((state, index) => (
            <div key={index} className="flex min-w-0 flex-col gap-2">
              <Label htmlFor={`share-title-${index}`} className="flex min-w-0 items-center gap-2">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                  <ItemIcon item={state.item} />
                </span>
                Title
              </Label>
              <Input
                id={`share-title-${index}`}
                value={state.item.title}
                maxLength={500}
                disabled={isSaving || Boolean(problemWith(state.item))}
                onChange={(e) => setTitle(index, e.target.value)}
              />
              <p className="truncate text-xs text-muted-foreground">{itemSubtitle(state.item)}</p>
              {state.error && <p className="text-xs text-destructive">{state.error}</p>}
            </div>
          ))}
          <div className="flex flex-col gap-2 sm:flex-row-reverse">
            <Button className="h-10 flex-1" disabled={isSaving || savable.length === 0} onClick={handleSave}>
              {isSaving ? 'Saving…' : savable.length === 1 ? 'Save' : `Save ${savable.length} items`}
            </Button>
            <Button variant="outline" className="h-10 flex-1" disabled={isSaving} onClick={handleDiscard}>
              Discard
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
