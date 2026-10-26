import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { MailCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { requestPasswordReset } from '@/api/auth'
import { extractErrorMessage } from '@/api/client'
import { useSlowHint } from '@/hooks/useSlowHint'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isPending, setIsPending] = useState(false)
  const slow = useSlowHint(isPending)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsPending(true)
    try {
      await requestPasswordReset(email.trim())
      setSent(true)
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
          <CardTitle className="text-2xl">Reset your password</CardTitle>
          <CardDescription>
            {sent ? 'Check your inbox' : "Enter your account's email and we'll send you a reset link."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {sent ? (
            <div className="flex flex-col items-center gap-3 text-center">
              <MailCheck className="size-10 text-primary" />
              <p className="text-sm">
                If an account exists for <span className="font-medium">{email.trim()}</span>, we've sent a link to
                reset the password. It expires in 30 minutes.
              </p>
              <p className="text-xs text-muted-foreground">
                Nothing after a few minutes? Check your spam folder, or try again.
              </p>
              <Button variant="outline" className="w-full" onClick={() => setSent(false)}>
                Send again
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button type="submit" disabled={isPending} className="w-full">
                {isPending ? 'Sending…' : 'Send reset link'}
              </Button>
              {slow && (
                <p className="text-center text-xs text-muted-foreground">
                  Waking up the server, this can take up to a minute on the free plan…
                </p>
              )}
            </form>
          )}
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Remembered it?{' '}
            <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
