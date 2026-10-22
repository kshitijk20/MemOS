import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Eye, EyeOff, ExternalLink, HelpCircle, Lock, Mail, User } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { useProfileQuery, useUpdateProfileMutation } from '@/hooks/useProfile'
import { cn } from '@/lib/utils'

function SectionIcon({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <span
      className={cn(
        'flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground',
        className,
      )}
    >
      {children}
    </span>
  )
}

function IconField({
  id,
  label,
  icon,
  trailing,
  hint,
  error,
  ...inputProps
}: React.ComponentProps<typeof Input> & {
  id: string
  label: string
  icon: ReactNode
  trailing?: ReactNode
  hint?: string
  error?: string
}) {
  const { className, ...rest } = inputProps
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground">
          {icon}
        </span>
        <Input
          id={id}
          aria-invalid={Boolean(error)}
          className={cn('h-10 pl-9', trailing && 'pr-9', className)}
          {...rest}
        />
        {trailing && (
          <div className="absolute top-1/2 right-1 -translate-y-1/2">{trailing}</div>
        )}
      </div>
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  )
}

function PasswordField({
  id,
  label,
  value,
  onChange,
  placeholder,
  error,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  placeholder: string
  error?: string
}) {
  const [visible, setVisible] = useState(false)

  return (
    <IconField
      id={id}
      label={label}
      icon={<Lock className="size-4" />}
      type={visible ? 'text' : 'password'}
      autoComplete="new-password"
      placeholder={placeholder}
      value={value}
      error={error}
      onChange={(e) => onChange(e.target.value)}
      trailing={
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="text-muted-foreground"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </Button>
      }
    />
  )
}

export function SettingsPage() {
  const { data: profile, isLoading } = useProfileQuery()
  const updateMutation = useUpdateProfileMutation()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordError, setPasswordError] = useState<string | null>(null)

  useEffect(() => {
    if (profile) {
      setFullName(profile.fullName)
      setEmail(profile.email)
    }
  }, [profile])

  const profileDirty =
    profile != null &&
    (fullName.trim() !== profile.fullName || email.trim().toLowerCase() !== profile.email.toLowerCase())

  const canResetPassword = newPassword.length > 0 && confirmPassword.length > 0

  async function handleProfileSubmit(event: FormEvent) {
    event.preventDefault()
    try {
      await updateMutation.mutateAsync({
        fullName: fullName.trim(),
        email: email.trim(),
      })
    } catch {
      // toast is shown by the mutation
    }
  }

  async function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault()
    setPasswordError(null)

    if (newPassword.length < 8) {
      setPasswordError('Use at least 8 characters.')
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('Passwords do not match.')
      return
    }

    try {
      await updateMutation.mutateAsync({
        fullName: profile?.fullName ?? fullName,
        email: profile?.email ?? email,
        newPassword,
      })
      setNewPassword('')
      setConfirmPassword('')
    } catch {
      // toast is shown by the mutation
    }
  }

  if (isLoading || !profile) {
    return (
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
        <div>
          <Skeleton className="h-8 w-36" />
          <Skeleton className="mt-2 h-4 w-72" />
        </div>
        <Skeleton className="h-56 w-full" />
        <Skeleton className="h-56 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-muted-foreground">Manage your account information and password.</p>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-start gap-3">
          <SectionIcon>
            <User className="size-4" />
          </SectionIcon>
          <div className="space-y-1">
            <CardTitle>Profile information</CardTitle>
            <CardDescription>Your name and email are used across MemOS.</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleProfileSubmit} className="flex flex-col gap-4">
            <IconField
              id="fullName"
              label="Name"
              icon={<User className="size-4" />}
              value={fullName}
              required
              maxLength={150}
              autoComplete="name"
              onChange={(e) => setFullName(e.target.value)}
            />
            <IconField
              id="email"
              label="Email address"
              icon={<Mail className="size-4" />}
              type="email"
              value={email}
              required
              autoComplete="email"
              hint="We'll use this email for important notifications."
              onChange={(e) => setEmail(e.target.value)}
            />
            <Button type="submit" className="h-10 w-full" disabled={!profileDirty || updateMutation.isPending}>
              {updateMutation.isPending && profileDirty ? 'Saving…' : 'Save changes'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-start gap-3">
          <SectionIcon className="bg-violet-500/15 text-violet-500">
            <Lock className="size-4" />
          </SectionIcon>
          <div className="space-y-1">
            <CardTitle>Reset password</CardTitle>
            <CardDescription>Choose a new password to keep your account secure.</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <form onSubmit={handlePasswordSubmit} autoComplete="off" className="flex flex-col gap-4">
            <PasswordField
              id="newPassword"
              label="New password"
              placeholder="Enter new password"
              value={newPassword}
              onChange={setNewPassword}
            />
            <PasswordField
              id="confirmPassword"
              label="Confirm new password"
              placeholder="Confirm new password"
              value={confirmPassword}
              onChange={setConfirmPassword}
              error={passwordError ?? undefined}
            />
            <p className="text-xs text-muted-foreground">
              Use at least 8 characters with a mix of letters, numbers, and symbols.
            </p>
            <Button
              type="submit"
              variant="secondary"
              className="h-10 w-full"
              disabled={!canResetPassword || updateMutation.isPending}
            >
              {updateMutation.isPending && canResetPassword ? 'Updating…' : 'Reset password'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-start gap-3">
          <SectionIcon className="bg-emerald-500/15 text-emerald-500">
            <HelpCircle className="size-4" />
          </SectionIcon>
          <div className="space-y-1">
            <CardTitle>Need help?</CardTitle>
            <CardDescription>If you face any issues, start a conversation and ask from your documents.</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <Button variant="outline" className="h-10 w-full" asChild>
            <Link to="/conversations">
              Contact support
              <ExternalLink className="size-4" />
            </Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
