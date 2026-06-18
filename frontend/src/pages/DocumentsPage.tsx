import { useState } from 'react'
import { GitBranch, Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { DocumentUploadDialog } from '@/components/DocumentUploadDialog'
import { DocumentGitDialog } from '@/components/DocumentGitDialog'
import { DocumentsTable } from '@/components/DocumentsTable'

export function DocumentsPage() {
  const [uploadOpen, setUploadOpen] = useState(false)
  const [gitOpen, setGitOpen] = useState(false)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Documents</h1>
          <p className="text-muted-foreground">Upload files or ingest a Git repository for retrieval.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setGitOpen(true)}>
            <GitBranch className="size-4" />
            Ingest from Git
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
    </div>
  )
}
