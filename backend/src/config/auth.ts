import { betterAuth } from 'better-auth';
import { drizzleAdapter } from 'better-auth/adapters/drizzle';
import { db } from '../db/index.js';
import { config } from './env.js';

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
});

export type Auth = typeof auth;
