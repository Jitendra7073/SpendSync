import type { NextConfig } from 'next';

// Static equivalent of the always-on headers Helmet used to set on every
// response — applied here instead of per-request Express middleware.
const securityHeaders = [
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'X-DNS-Prefetch-Control', value: 'off' },
  { key: 'X-Download-Options', value: 'noopen' },
  { key: 'Referrer-Policy', value: 'no-referrer' },
];

const nextConfig: NextConfig = {
  async headers() {
    return [
      {
        source: '/api/:path*',
        headers: securityHeaders,
      },
    ];
  },
};

export default nextConfig;
