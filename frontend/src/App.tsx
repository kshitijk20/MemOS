import { Navigate, Route, BrowserRouter, Routes } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from '@/components/ui/sonner'
import { queryClient } from '@/lib/query-client'
import { AuthProvider } from '@/lib/auth-context'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { ProtectedRoute } from '@/components/ProtectedRoute'
import { AppShell } from '@/components/AppShell'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { DocumentsPage } from '@/pages/DocumentsPage'
import { ConversationsPage } from '@/pages/ConversationsPage'
import { ConversationDetailPage } from '@/pages/ConversationDetailPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<AppShell />}>
                  <Route path="/" element={<Navigate to="/documents" replace />} />
                  <Route path="/documents" element={<DocumentsPage />} />
                  <Route path="/conversations" element={<ConversationsPage />} />
                  <Route path="/conversations/:id" element={<ConversationDetailPage />} />
                </Route>
              </Route>
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
        <Toaster />
      </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
