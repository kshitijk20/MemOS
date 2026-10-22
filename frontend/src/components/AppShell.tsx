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
      <header className="sticky top-0 z-50 border-b bg-background/80 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <div className="flex items-center gap-6">
            <span className="flex items-center gap-2 text-lg font-semibold tracking-tight">
              <span className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-primary-foreground text-[11px] font-bold leading-none">M</span>
              MemOS
            </span>
            <nav className="flex items-center gap-1">
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
          <div className="flex items-center gap-3">
            {user && <span className="text-sm text-muted-foreground">{user.email}</span>}
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
              Logout
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
