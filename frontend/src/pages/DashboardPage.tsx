import { Link } from 'react-router-dom'
import { FileText, MessageSquare, CheckCircle2, AlertCircle, Loader2, GitBranch, Upload, MessageSquarePlus, ChevronRight } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/StatusBadge'
import { AnswersToImprove } from '@/components/AnswersToImprove'
import { useStatsQuery } from '@/hooks/useStats'
import { useDocumentsQuery } from '@/hooks/useDocuments'
import { useConversationsQuery } from '@/hooks/useConversations'
import { useAuth } from '@/lib/use-auth'

interface StatCardProps {
  label: string
  value: number | undefined
  icon: React.ReactNode
  description?: string
  isLoading: boolean
}

function StatCard({ label, value, icon, description, isLoading }: StatCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <span className="text-muted-foreground">{icon}</span>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-8 w-16" />
        ) : (
          <p className="text-3xl font-bold">{value ?? 0}</p>
        )}
        {description && (
          <p className="mt-1 text-xs text-muted-foreground">{description}</p>
        )}
      </CardContent>
    </Card>
  )
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

export function DashboardPage() {
  const { user } = useAuth()
  const { data: stats, isLoading: statsLoading } = useStatsQuery()
  const { data: recentDocs, isLoading: docsLoading } = useDocumentsQuery(0, 5)
  const { data: recentConvs, isLoading: convsLoading } = useConversationsQuery(0, 5)

  const greeting = getGreeting()

  return (
    <div className="flex flex-col gap-8">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{greeting}, {user?.fullName?.split(' ')[0] ?? 'there'}</h1>
          <p className="text-muted-foreground">Here's what's happening in your knowledge base.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link to="/documents">
              <Upload className="size-4" />
              Upload doc
            </Link>
          </Button>
          <Button size="sm" asChild>
            <Link to="/conversations">
              <MessageSquarePlus className="size-4" />
              New chat
            </Link>
          </Button>
        </div>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard
          label="Total documents"
          value={stats?.totalDocuments}
          icon={<FileText className="size-4" />}
          isLoading={statsLoading}
        />
        <StatCard
          label="Ready"
          value={stats?.readyDocuments}
          icon={<CheckCircle2 className="size-4 text-green-500" />}
          description="indexed & searchable"
          isLoading={statsLoading}
        />
        <StatCard
          label="Conversations"
          value={stats?.totalConversations}
          icon={<MessageSquare className="size-4" />}
          isLoading={statsLoading}
        />
        <StatCard
          label="Failed"
          value={stats?.failedDocuments}
          icon={<AlertCircle className="size-4 text-destructive" />}
          description={stats?.processingDocuments ? `${stats.processingDocuments} processing` : undefined}
          isLoading={statsLoading}
        />
      </div>

      {/* Recent items */}
      <div className="grid gap-6 md:grid-cols-2">
        {/* Recent documents */}
        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Recent documents</h2>
            <Button variant="ghost" size="sm" className="text-xs" asChild>
              <Link to="/documents">View all</Link>
            </Button>
          </div>
          <Card className="overflow-hidden">
            {docsLoading ? (
              <div className="flex flex-col gap-3 p-4">
                {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            ) : !recentDocs || recentDocs.items.length === 0 ? (
              <div className="flex flex-col items-center gap-3 py-10 text-center">
                <FileText className="size-8 text-muted-foreground/40" />
                <p className="text-sm text-muted-foreground">No documents yet</p>
                <Button size="sm" variant="outline" asChild>
                  <Link to="/documents">
                    <Upload className="size-4" />
                    Upload a file
                  </Link>
                </Button>
              </div>
            ) : (
              <ul className="divide-y">
                {recentDocs.items.map((doc) => (
                  <li key={doc.id} className="flex items-center gap-3 px-4 py-3">
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-muted">
                      {doc.sourceType === 'GIT_REPOSITORY'
                        ? <GitBranch className="size-3.5 text-muted-foreground" />
                        : <FileText className="size-3.5 text-muted-foreground" />}
                    </div>
                    <div className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate text-sm font-medium">{doc.title}</span>
                      <span className="text-xs text-muted-foreground">{formatDate(doc.createdAt)}</span>
                    </div>
                    <StatusBadge status={doc.status} />
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        {/* Recent conversations */}
        <div className="flex min-w-0 flex-col gap-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Recent conversations</h2>
            <Button variant="ghost" size="sm" className="text-xs" asChild>
              <Link to="/conversations">View all</Link>
            </Button>
          </div>
          <Card className="overflow-hidden">
            {convsLoading ? (
              <div className="flex flex-col gap-3 p-4">
                {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
              </div>
            ) : !recentConvs || recentConvs.items.length === 0 ? (
              <div className="flex flex-col items-center gap-3 py-10 text-center">
                <MessageSquare className="size-8 text-muted-foreground/40" />
                <p className="text-sm text-muted-foreground">No conversations yet</p>
                <Button size="sm" asChild>
                  <Link to="/conversations">
                    <MessageSquarePlus className="size-4" />
                    Start a chat
                  </Link>
                </Button>
              </div>
            ) : (
              <ul className="divide-y">
                {recentConvs.items.map((conv) => (
                  <li key={conv.id}>
                    <Link
                      to={`/conversations/${conv.id}`}
                      className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-muted/40"
                    >
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-muted">
                        <MessageSquare className="size-3.5 text-muted-foreground" />
                      </div>
                      <div className="flex min-w-0 flex-1 flex-col">
                        <span className="truncate text-sm font-medium">{conv.title ?? 'Untitled conversation'}</span>
                        <div className="flex items-center gap-1.5">
                          <Badge variant="secondary" className="text-[10px] px-1 py-0">{conv.provider}</Badge>
                          <span className="text-xs text-muted-foreground">{formatDate(conv.updatedAt)}</span>
                        </div>
                      </div>
                      <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <AnswersToImprove helpful={stats?.helpfulAnswers} unhelpful={stats?.unhelpfulAnswers} />

      {/* Ingestion alert if any processing/failed */}
      {!statsLoading && (stats?.processingDocuments ?? 0) > 0 && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-600 dark:text-amber-400">
          <Loader2 className="size-4 shrink-0 animate-spin" />
          <span>
            {stats!.processingDocuments} document{stats!.processingDocuments > 1 ? 's are' : ' is'} being processed. This page refreshes automatically.
          </span>
        </div>
      )}
    </div>
  )
}

function getGreeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}
