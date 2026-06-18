import { CircleCheck, CircleX, Clock, Loader2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { DocumentStatus } from '@/api/types'

const STATUS_CONFIG: Record<DocumentStatus, { label: string; className: string; icon: typeof Clock }> = {
  PENDING: { label: 'Pending', className: '', icon: Clock },
  PROCESSING: { label: 'Processing', className: '', icon: Loader2 },
  COMPLETED: {
    label: 'Completed',
    className: 'border-green-600/30 text-green-600 dark:text-green-400',
    icon: CircleCheck,
  },
  FAILED: { label: 'Failed', className: '', icon: CircleX },
}

export function StatusBadge({ status }: { status: DocumentStatus }) {
  const config = STATUS_CONFIG[status]
  const Icon = config.icon
  const variant = status === 'FAILED' ? 'destructive' : status === 'COMPLETED' ? 'outline' : 'secondary'

  return (
    <Badge variant={variant} className={cn(config.className)}>
      <Icon className={cn('size-3', status === 'PROCESSING' && 'animate-spin')} />
      {config.label}
    </Badge>
  )
}
