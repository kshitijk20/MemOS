import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getRegistrationPolicy } from '@/api/auth'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/lib/use-auth'
import { useSlowHint } from '@/hooks/useSlowHint'
import { extractErrorMessage } from '@/api/client'

export function RegisterPage() {
  const { register, verifyRegistration } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  // Step 2 (enter the emailed code) once the details are sent.
  const [codeSent, setCodeSent] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // If this check fails, show the normal form and let the server's response explain any problem.
  const { data: policy } = useQuery({ queryKey: ['registration-policy'], queryFn: getRegistrationPolicy, retry: false })
  const closed = policy?.open === false
  const [isPending, setIsPending] = useState(false)
  const slow = useSlowHint(isPending)

  async function sendCode(): Promise<boolean> {
    setError(null)
    setNotice(null)
    setIsPending(true)
    try {
      await register(email.trim(), password, fullName.trim())
      setCodeSent(true)
      setCode('')
      return true
    } catch (err) {
      setError(extractErrorMessage(err))
      return false
    } finally {
      setIsPending(false)
    }
  }

  async function handleDetailsSubmit(event: FormEvent) {
    event.preventDefault()
    await sendCode()
  }

  async function handleResend() {
    if (await sendCode()) {
      setNotice('We sent a new code. Only the newest code works.')
    }
  }

  async function handleCodeSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setNotice(null)
    setIsPending(true)
    try {
      await verifyRegistration(email.trim(), code.trim(), fullName.trim())
      navigate('/documents', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err))
    } finally {
      setIsPending(false)
    }
  }

  function description() {
    if (closed) return 'Registration is closed on this server.'
    if (codeSent) return `We sent a 6-digit code to ${email.trim()}.`
    return 'Sign up to start using MemOS'
  }

  return (
    <div className="flex min-h-svh items-center justify-center px-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <div className="mb-1 flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-primary-foreground text-base font-bold">
            M
          </div>
          <CardTitle className="text-2xl">{codeSent ? 'Check your email' : 'Create an account'}</CardTitle>
          <CardDescription className="break-words">{description()}</CardDescription>
        </CardHeader>
        <CardContent>
          {closed ? (
            <p className="text-center text-sm text-muted-foreground">
              New accounts aren't being accepted right now. If you already have one, sign in below.
            </p>
          ) : codeSent ? (
            <form onSubmit={handleCodeSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="code">Verification code</Label>
                <Input
                  id="code"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9 ]{6,7}"
                  maxLength={7}
                  required
                  autoFocus
                  placeholder="123456"
                  className="text-center font-mono text-lg tracking-[0.4em]"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                />
                <p className="text-xs text-muted-foreground">
                  The code expires in 15 minutes. Can't find it? Check your spam folder.
                </p>
              </div>
              {notice && <p className="text-sm text-muted-foreground">{notice}</p>}
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button type="submit" disabled={isPending} className="w-full">
                {isPending ? 'Verifying…' : 'Verify and create account'}
              </Button>
              <div className="flex justify-between text-sm">
                <button
                  type="button"
                  className="text-muted-foreground underline-offset-4 hover:underline"
                  onClick={() => {
                    setCodeSent(false)
                    setError(null)
                    setNotice(null)
                  }}
                >
                  Change details
                </button>
                <button
                  type="button"
                  disabled={isPending}
                  className="font-medium text-primary underline-offset-4 hover:underline disabled:opacity-50"
                  onClick={handleResend}
                >
                  Resend code
                </button>
              </div>
            </form>
          ) : (
            <form onSubmit={handleDetailsSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-2">
                <Label htmlFor="fullName">Full name</Label>
                <Input
                  id="fullName"
                  type="text"
                  autoComplete="name"
                  required
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                />
              </div>
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
                  autoComplete="new-password"
                  required
                  minLength={8}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button type="submit" disabled={isPending} className="w-full">
                {isPending ? 'Sending code…' : 'Continue'}
              </Button>
              <p className="text-center text-xs text-muted-foreground">
                We'll email you a code to confirm the address is yours.
              </p>
            </form>
          )}
          {slow && (
            <p className="mt-2 text-center text-xs text-muted-foreground">
              Waking up the server, this can take up to a minute on the free plan…
            </p>
          )}
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
