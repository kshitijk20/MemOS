import { useState } from 'react'
import { GitBranch, Globe, Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DocumentUploadDialog } from '@/components/DocumentUploadDialog'
import { DocumentGitDialog } from '@/components/DocumentGitDialog'
import { DocumentUrlDialog } from '@/components/DocumentUrlDialog'
import { DocumentsTable } from '@/components/DocumentsTable'

export function DocumentsPage() {
  const [uploadOpen, setUploadOpen] = useState(false)
  const [gitOpen, setGitOpen] = useState(false)
  const [urlOpen, setUrlOpen] = useState(false)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Documents</h1>
          <p className="text-muted-foreground">Upload files, ingest a Git repository, or fetch a web page.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setUrlOpen(true)}>
            <Globe className="size-4" />
            From URL
          </Button>
          <Button variant="outline" onClick={() => setGitOpen(true)}>
            <GitBranch className="size-4" />
            From Git
          </Button>
          <Button onClick={() => setUploadOpen(true)}>
            <Upload className="size-4" />
            Upload file
          </Button>
        </div>
      </div>

      <DocumentsTable />

      <DocumentUploadDialog open={uploadOpen} onOpenChange={setUploadOpen} />
      <DocumentGitDialog open={gitOpen} onOpenChange={setGitOpen} />
      <DocumentUrlDialog open={urlOpen} onOpenChange={setUrlOpen} />
    </div>
  )
}
