import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getProfile, updateProfile } from '@/api/users'
import { extractErrorMessage } from '@/api/client'
import { useAuth } from '@/lib/use-auth'
import type { UpdateProfileRequest } from '@/api/types'

export function useProfileQuery() {
  return useQuery({
    queryKey: ['profile'],
    queryFn: getProfile,
  })
}

export function useUpdateProfileMutation() {
  const queryClient = useQueryClient()
  const { updateUser } = useAuth()

  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => updateProfile(data),
    onSuccess: (updated, variables) => {
      queryClient.setQueryData(['profile'], updated)
      updateUser({ email: updated.email, fullName: updated.fullName })
      toast.success(variables.newPassword ? 'Password updated' : 'Profile updated')
    },
    onError: (error) => {
      toast.error(extractErrorMessage(error))
    },
  })
}
