import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { LogOut, MessageSquare, FileText, Settings, Sun, Moon, LayoutDashboard } from 'lucide-react'
import { useTheme } from 'next-themes'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAuth } from '@/lib/use-auth'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/documents', label: 'Documents', icon: FileText },
  { to: '/conversations', label: 'Conversations', icon: MessageSquare },
  { to: '/settings', label: 'Settings', icon: Settings },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { resolvedTheme, setTheme } = useTheme()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="sticky top-0 z-50 border-b bg-background/80 pt-[env(safe-area-inset-top)] backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-2 px-4">
          <div className="flex min-w-0 items-center gap-6">
            <span className="flex shrink-0 items-center gap-2 text-lg font-semibold tracking-tight">
              <span className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-primary-foreground text-[11px] font-bold leading-none">M</span>
              MemOS
            </span>
            {/* Tablet and up: navigation in the header. Phones use the bottom tab bar instead. */}
            <nav className="hidden items-center gap-1 md:flex">
              {navItems.map(({ to, label, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-secondary text-foreground'
                        : 'text-muted-foreground hover:bg-secondary/50 hover:text-foreground',
                    )
                  }
                >
                  <Icon className="size-4" />
                  {label}
                </NavLink>
              ))}
            </nav>
          </div>
          <div className="flex shrink-0 items-center gap-1 sm:gap-3">
            {user && <span className="hidden max-w-[14rem] truncate text-sm text-muted-foreground lg:inline">{user.email}</span>}
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}
              aria-label="Toggle theme"
            >
              {resolvedTheme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </Button>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="size-4" />
              Log out
            </Button>
          </div>
        </div>
      </header>
      {/* Bottom padding on phones keeps content clear of the tab bar. */}
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 pt-4 pb-[calc(5rem+env(safe-area-inset-bottom))] md:py-6">
        <Outlet />
      </main>
      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-50 border-t bg-background/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-md md:hidden"
      >
        <div className="mx-auto grid h-16 max-w-md grid-cols-4">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex flex-col items-center justify-center gap-1 text-[11px] font-medium transition-colors',
                  isActive ? 'text-foreground' : 'text-muted-foreground',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <span className={cn('rounded-full px-4 py-1 transition-colors', isActive && 'bg-secondary')}>
                    <Icon className="size-5" />
                  </span>
                  {label === 'Conversations' ? 'Chats' : label}
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
