import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/lib/use-auth'
import { useSlowHint } from '@/hooks/useSlowHint'
import { extractErrorMessage } from '@/api/client'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isPending, setIsPending] = useState(false)
  const slow = useSlowHint(isPending)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsPending(true)
    try {
      await login(email, password)
      navigate('/documents', { replace: true })
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
          <CardTitle className="text-2xl">Sign in</CardTitle>
          <CardDescription>Sign in to your MemOS account</CardDescription>
        </CardHeader>
        <CardContent>
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
            <div className="flex flex-col gap-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" disabled={isPending} className="w-full">
              {isPending ? 'Signing in…' : 'Sign in'}
            </Button>
            {slow && (
              <p className="text-center text-xs text-muted-foreground">
                Waking up the server, this can take up to a minute on the free plan…
              </p>
            )}
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            <Link to="/forgot-password" className="font-medium text-primary underline-offset-4 hover:underline">
              Forgot your password?
            </Link>
          </p>
          <p className="mt-2 text-center text-sm text-muted-foreground">
            Don't have an account?{' '}
            <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
