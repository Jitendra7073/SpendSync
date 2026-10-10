import { z } from 'zod';

// Next.js loads .env/.env.local automatically before this module runs.
// Environment variable schema validation
const envSchema = z.object({
  // Server
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  PORT: z.string().default('3000'),
  API_URL: z.string().url().default('http://localhost:3000'),

  // Database
  DATABASE_URL: z.string().url(),

  // Better Auth
  AUTH_SECRET: z.string().min(32),
  AUTH_TRUST_HOST: z.string().default('true'),
  AUTH_SESSION_EXPIRY: z.string().default('7d'),

  // Email
  SMTP_HOST: z.string().optional(),
  SMTP_PORT: z.string().optional(),
  SMTP_USER: z.string().optional(),
  SMTP_PASSWORD: z.string().optional(),
  EMAIL_FROM: z.string().email().optional(),
  // Who receives support reports (comma-separated). No default: set it in .env / the host's environment.
  SUPPORT_EMAIL_TO: z.string().optional(),

  // Rate Limiting
  RATE_LIMIT_WINDOW_MS: z.string().default('900000'),
  RATE_LIMIT_MAX_REQUESTS: z.string().default('100'),
  AUTH_RATE_LIMIT_MAX: z.string().default('5'),

  // CORS
  ALLOWED_ORIGINS: z.string().default('http://localhost:3000'),

  // Bills (Cloudinary). Without the three keys, bill endpoints answer 503 BILLS_NOT_CONFIGURED.
  CLOUDINARY_CLOUD_NAME: z.string().optional(),
  CLOUDINARY_API_KEY: z.string().optional(),
  CLOUDINARY_API_SECRET: z.string().optional(),
  CRON_SECRET: z.string().optional(),
  BILLS_MAX_BYTES_PER_USER: z.string().default('524288000'),
  BILLS_MAX_UPLOADS_PER_DAY: z.string().default('50'),

  // Logging
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
});

// Parse and validate environment variables
const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  console.error('❌ Invalid environment variables:', parsed.error.flatten().fieldErrors);
  throw new Error('Invalid environment variables');
}

const env = parsed.data;

// Export typed configuration
export const config = {
  env: env.NODE_ENV,
  port: parseInt(env.PORT, 10),
  apiUrl: env.API_URL,
  
  database: {
    url: env.DATABASE_URL,
  },
  
  auth: {
    secret: env.AUTH_SECRET,
    trustHost: env.AUTH_TRUST_HOST === 'true',
    sessionExpiry: env.AUTH_SESSION_EXPIRY,
  },
  
  email: {
    host: env.SMTP_HOST,
    port: env.SMTP_PORT ? parseInt(env.SMTP_PORT, 10) : undefined,
    user: env.SMTP_USER,
    password: env.SMTP_PASSWORD,
    from: env.EMAIL_FROM,
  },
  
  rateLimit: {
    windowMs: parseInt(env.RATE_LIMIT_WINDOW_MS, 10),
    maxRequests: parseInt(env.RATE_LIMIT_MAX_REQUESTS, 10),
    authMax: parseInt(env.AUTH_RATE_LIMIT_MAX, 10),
  },
  
  bills: {
    cloudinary:
      env.CLOUDINARY_CLOUD_NAME && env.CLOUDINARY_API_KEY && env.CLOUDINARY_API_SECRET
        ? { cloudName: env.CLOUDINARY_CLOUD_NAME, apiKey: env.CLOUDINARY_API_KEY, apiSecret: env.CLOUDINARY_API_SECRET }
        : null,
    maxBytes: parseInt(env.BILLS_MAX_BYTES_PER_USER, 10),
    maxUploadsPerDay: parseInt(env.BILLS_MAX_UPLOADS_PER_DAY, 10),
    cronSecret: env.CRON_SECRET,
    notificationUrl: `${env.API_URL.replace(/\/$/, '')}/api/bills/webhook`,
  },

  cors: {
    allowedOrigins: env.ALLOWED_ORIGINS.split(',').map((origin) => origin.trim()),
  },
  
  logging: {
    level: env.LOG_LEVEL,
  },
  
  isDevelopment: env.NODE_ENV === 'development',
  isProduction: env.NODE_ENV === 'production',
  isTest: env.NODE_ENV === 'test',
} as const;

export type Config = typeof config;
