import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { confirmPasswordReset } from '@/api/auth'
import { extractErrorMessage } from '@/api/client'
import { useSlowHint } from '@/hooks/useSlowHint'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isPending, setIsPending] = useState(false)
  const slow = useSlowHint(isPending)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (password.length < 8) {
      setError('Use at least 8 characters.')
      return
    }
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }
    setIsPending(true)
    try {
      await confirmPasswordReset(token, password)
      toast.success('Password updated. Sign in with your new password.')
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err))
    } finally {
      setIsPending(false)
    }
  }

  return (
    <div className="flex min-h-svh items-center justify-center px-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <div className="mb-1 flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground text-base font-bold">
            M
          </div>
          <CardTitle className="text-2xl">Choose a new password</CardTitle>
          <CardDescription>You'll be signed out of other devices.</CardDescription>
        </CardHeader>
        <CardContent>
          {!token ? (
            <p className="text-center text-sm text-muted-foreground">
              This link is incomplete. Open the link from your email again, or{' '}
              <Link to="/forgot-password" className="font-medium text-primary underline-offset-4 hover:underline">
                request a new one
              </Link>
              .
            </p>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="password">New password</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label htmlFor="confirm">Confirm new password</Label>
                <Input
                  id="confirm"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                />
              </div>
              {error && (
                <p className="text-sm text-destructive">
                  {error}{' '}
                  {error.includes('expired') && (
                    <Link to="/forgot-password" className="font-medium underline underline-offset-4">
                      Request a new link
                    </Link>
                  )}
                </p>
              )}
              <Button type="submit" disabled={isPending} className="w-full">
                {isPending ? 'Saving…' : 'Set new password'}
              </Button>
              {slow && (
                <p className="text-center text-xs text-muted-foreground">
                  Waking up the server, this can take up to a minute on the free plan…
                </p>
              )}
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
