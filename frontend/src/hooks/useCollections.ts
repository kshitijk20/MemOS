import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { createCollection, deleteCollection, listCollections, updateCollection } from '@/api/collections'
import { extractErrorMessage } from '@/api/client'

export function useCollectionsQuery() {
  return useQuery({
    queryKey: ['collections'],
    queryFn: listCollections,
  })
}

export function useCreateCollectionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ name, documentIds }: { name: string; documentIds: string[] }) => createCollection(name, documentIds),
    onSuccess: (collection) => {
      toast.success(`Collection "${collection.name}" created`)
      queryClient.invalidateQueries({ queryKey: ['collections'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useUpdateCollectionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...changes }: { id: string; name?: string; documentIds?: string[] }) =>
      updateCollection(id, changes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['collections'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}

export function useDeleteCollectionMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteCollection(id),
    onSuccess: () => {
      toast.success('Collection deleted')
      queryClient.invalidateQueries({ queryKey: ['collections'] })
      // Chats limited to it now search everything.
      queryClient.invalidateQueries({ queryKey: ['conversations'] })
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}
