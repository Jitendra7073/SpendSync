import { betterAuth } from 'better-auth';
import { bearer } from 'better-auth/plugins/bearer';
import { drizzleAdapter } from 'better-auth/adapters/drizzle';
import { db } from '../db/index';
import { config } from './env';

/**
 * Better Auth Configuration
 * Handles authentication, session management, and user management
 * Email verification is disabled - all users are auto-verified for development
 */
export const auth = betterAuth({
  database: drizzleAdapter(db, {
    provider: 'pg',
  }),
  
  // Email and password authentication
  emailAndPassword: {
    enabled: true,
    requireEmailVerification: false, // Disabled for developer convenience
  },
  
  // Session configuration
  session: {
    expiresIn: 60 * 60 * 24 * 7, // 7 days in seconds
    updateAge: 60 * 60 * 24, // 1 day in seconds
  },
  
  // Base URL - must include protocol and host
  baseURL: config.apiUrl,
  
  // Secret for signing tokens
  secret: config.auth.secret,
  
  // Trust host (for reverse proxies)
  trustedOrigins: config.cors.allowedOrigins,

  // Accept `Authorization: Bearer <token>` in addition to the session
  // cookie — the Android client is a native app with no shared cookie
  // jar across process restarts, so it authenticates via bearer token.
  plugins: [bearer()],
});

export type Auth = typeof auth;
