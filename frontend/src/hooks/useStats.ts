import { useQuery } from '@tanstack/react-query'
import { fetchStats } from '@/api/stats'

export function useStatsQuery() {
  return useQuery({
    queryKey: ['stats'],
    queryFn: fetchStats,
    refetchInterval: 15_000,
  })
}
