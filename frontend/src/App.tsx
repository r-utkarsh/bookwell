import { lazy, useEffect } from 'react'
import { BrowserRouter, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { Toaster } from 'sonner'
import { AuthProvider } from '@/auth/AuthContext'
import { RequireAuth } from '@/auth/RequireAuth'
import { AppShell } from '@/components/AppShell'
import { PublicLayout } from '@/components/PublicLayout'
import { ForbiddenPage, NotFoundPage } from '@/pages/NotFoundPage'

const HomePage = lazy(() => import('@/pages/HomePage').then((module) => ({ default: module.HomePage })))
const ProvidersPage = lazy(() => import('@/pages/ProvidersPage').then((module) => ({ default: module.ProvidersPage })))
const ProviderDetailPage = lazy(() => import('@/pages/ProviderDetailPage').then((module) => ({ default: module.ProviderDetailPage })))
const BookPage = lazy(() => import('@/pages/BookPage').then((module) => ({ default: module.BookPage })))
const ForProvidersPage = lazy(() => import('@/pages/ForProvidersPage').then((module) => ({ default: module.ForProvidersPage })))
const LoginPage = lazy(() => import('@/pages/LoginPage').then((module) => ({ default: module.LoginPage })))
const RegisterPage = lazy(() => import('@/pages/RegisterPage').then((module) => ({ default: module.RegisterPage })))
const ProviderRegisterPage = lazy(() => import('@/pages/ProviderRegisterPage').then((module) => ({ default: module.ProviderRegisterPage })))
const VerifyEmailPage = lazy(() => import('@/pages/VerifyEmailPage').then((module) => ({ default: module.VerifyEmailPage })))
const CustomerDashboardPage = lazy(() => import('@/pages/CustomerDashboardPage').then((module) => ({ default: module.CustomerDashboardPage })))

const ProviderOverviewPage = lazy(() => import('@/pages/provider/ProviderOverviewPage').then((module) => ({ default: module.ProviderOverviewPage })))
const ProviderCalendarPage = lazy(() => import('@/pages/provider/ProviderCalendarPage').then((module) => ({ default: module.ProviderCalendarPage })))
const ProviderServicesPage = lazy(() => import('@/pages/provider/ProviderServicesPage').then((module) => ({ default: module.ProviderServicesPage })))
const ProviderAvailabilityPage = lazy(() => import('@/pages/provider/ProviderAvailabilityPage').then((module) => ({ default: module.ProviderAvailabilityPage })))
const ProviderProfilePage = lazy(() => import('@/pages/provider/ProviderProfilePage').then((module) => ({ default: module.ProviderProfilePage })))

const AdminOverviewPage = lazy(() => import('@/pages/admin/AdminOverviewPage').then((module) => ({ default: module.AdminOverviewPage })))
const AdminProvidersPage = lazy(() => import('@/pages/admin/AdminProvidersPage').then((module) => ({ default: module.AdminProvidersPage })))
const AdminCustomersPage = lazy(() => import('@/pages/admin/AdminCustomersPage').then((module) => ({ default: module.AdminCustomersPage })))
const AdminBookingsPage = lazy(() => import('@/pages/admin/AdminBookingsPage').then((module) => ({ default: module.AdminBookingsPage })))
const AdminNotificationsPage = lazy(() => import('@/pages/admin/AdminNotificationsPage').then((module) => ({ default: module.AdminNotificationsPage })))

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' })
  }, [pathname])
  return null
}

function Root() {
  return (
    <>
      <ScrollToTop />
      <Outlet />
      <Toaster
        position="top-right"
        richColors
        closeButton
        toastOptions={{
          style: {
            fontFamily: 'Manrope, sans-serif',
            borderRadius: '1rem',
          },
        }}
      />
    </>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Root />}>
            <Route element={<PublicLayout />}>
              <Route index element={<HomePage />} />
              <Route path="providers" element={<ProvidersPage />} />
              <Route path="providers/:providerId" element={<ProviderDetailPage />} />
              <Route path="providers/:providerId/book" element={<BookPage />} />
              <Route path="for-providers" element={<ForProvidersPage />} />
              <Route path="login" element={<LoginPage />} />
              <Route path="register" element={<RegisterPage />} />
              <Route path="register/provider" element={<ProviderRegisterPage />} />
              <Route path="verify-email" element={<VerifyEmailPage />} />
              <Route
                path="account"
                element={
                  <RequireAuth roles={['CUSTOMER']}>
                    <CustomerDashboardPage />
                  </RequireAuth>
                }
              />
              <Route path="forbidden" element={<ForbiddenPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>

            <Route
              path="provider"
              element={
                <RequireAuth roles={['PROVIDER']}>
                  <AppShell />
                </RequireAuth>
              }
            >
              <Route index element={<ProviderOverviewPage />} />
              <Route path="calendar" element={<ProviderCalendarPage />} />
              <Route path="services" element={<ProviderServicesPage />} />
              <Route path="availability" element={<ProviderAvailabilityPage />} />
              <Route path="profile" element={<ProviderProfilePage />} />
            </Route>

            <Route
              path="admin"
              element={
                <RequireAuth roles={['ADMIN']}>
                  <AppShell />
                </RequireAuth>
              }
            >
              <Route index element={<AdminOverviewPage />} />
              <Route path="providers" element={<AdminProvidersPage />} />
              <Route path="customers" element={<AdminCustomersPage />} />
              <Route path="bookings" element={<AdminBookingsPage />} />
              <Route path="notifications" element={<AdminNotificationsPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
