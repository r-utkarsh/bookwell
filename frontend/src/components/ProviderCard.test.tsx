import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ProviderCard } from '@/components/ProviderCard'
import type { ProviderCard as ProviderCardType } from '@/types/api'

const baseProvider: ProviderCardType = {
  id: 'provider-1',
  displayName: 'Aarohi Sen',
  businessName: 'Aarohi Studio',
  category: 'Salon & Spa',
  description: 'Slow, thoughtful hair and skin rituals.',
  city: 'Pune',
  address: 'Kalyani Nagar',
  phone: '+91 90000 00000',
  timeZone: 'Asia/Kolkata',
  currency: 'INR',
  featured: true,
  status: 'APPROVED',
  serviceCount: 3,
  startingPrice: 1200,
}

function renderCard(provider: ProviderCardType = baseProvider) {
  return render(
    <MemoryRouter>
      <ProviderCard provider={provider} />
    </MemoryRouter>,
  )
}

describe('ProviderCard', () => {
  it('shows provider details and links to the profile', () => {
    renderCard()

    expect(screen.getByRole('heading', { name: 'Aarohi Sen' })).toBeInTheDocument()
    expect(screen.getByText('Aarohi Studio')).toBeInTheDocument()
    expect(screen.getByText('Salon & Spa')).toBeInTheDocument()
    expect(screen.getByText('Pune')).toBeInTheDocument()
    expect(screen.getByText('3 services')).toBeInTheDocument()
    expect(screen.getByText(/From/)).toHaveTextContent('₹1,200')
    expect(screen.getByText('Featured')).toBeInTheDocument()
    expect(screen.getByRole('link')).toHaveAttribute('href', '/providers/provider-1')
  })

  it('uses sensible fallbacks for a sparse provider', () => {
    renderCard({
      ...baseProvider,
      description: null,
      city: null,
      featured: false,
      serviceCount: 1,
      startingPrice: null,
    })

    expect(
      screen.getByText('A thoughtful independent service provider, ready to welcome you.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Online')).toBeInTheDocument()
    expect(screen.getByText('1 service')).toBeInTheDocument()
    expect(screen.getByText('View services')).toBeInTheDocument()
    expect(screen.queryByText('Featured')).not.toBeInTheDocument()
  })
})
