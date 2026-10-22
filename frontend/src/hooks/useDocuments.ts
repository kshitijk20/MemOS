import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  deleteDocument,
  fetchDocumentProgress,
  ingestGitRepository,
  ingestUrl,
  listDocuments,
  reingestDocument,
  retryDocument,
  uploadDocument,
} from '@/api/documents'
import { extractErrorMessage } from '@/api/client'
import type { GitIngestRequest, UrlIngestRequest } from '@/api/types'

const ACTIVE_STATUSES = new Set(['PENDING', 'PROCESSING'])

export function useDocumentProgressQuery(documentId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['document-progress', documentId],
    queryFn: () => fetchDocumentProgress(documentId),
    enabled,
    refetchInterval: 1500,
    retry: false,
  })
}

export function useDocumentsQuery(page: number, size: number) {
  return useQuery({
    queryKey: ['documents', page, size],
    queryFn: () => listDocuments(page, size),
    refetchInterval: (query) => {
      const data = query.state.data
      const hasActive = data?.items.some((doc) => ACTIVE_STATUSES.has(doc.status))
      return hasActive ? 3000 : false
    },
  })
}

export function useUploadDocumentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, title }: { file: File; title: string }) => uploadDocument(file, title),
    onSuccess: () => {
      toast.success('Document upload started')
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useGitIngestMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: GitIngestRequest) => ingestGitRepository(payload),
    onSuccess: () => {
      toast.success('Repository ingestion started')
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useUrlIngestMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: UrlIngestRequest) => ingestUrl(payload),
    onSuccess: () => {
      toast.success('Web page ingestion started')
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useRetryDocumentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => retryDocument(id),
    onSuccess: () => {
      toast.success('Retrying document ingestion…')
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useReingestDocumentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file?: File }) => reingestDocument(id, file),
    onSuccess: () => {
      toast.success('Re-ingesting document…')
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      queryClient.invalidateQueries({ queryKey: ['stats'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useDeleteDocumentMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteDocument(id),
    onSuccess: () => {
      toast.success('Document deleted')
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}
