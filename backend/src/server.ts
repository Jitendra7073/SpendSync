import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import compression from 'compression';
import cookieParser from 'cookie-parser';
import { config } from './config/env.js';
import { auth } from './config/auth.js';
import { logger } from './utils/logger.js';
import { errorHandler, notFoundHandler } from './middleware/error.middleware.js';
import { apiLimiter } from './middleware/rateLimiter.middleware.js';
import routes from './routes/index.js';

/**
 * Create and configure Express application
 */
const app = express();

// Security middleware
app.use(helmet({
  contentSecurityPolicy: config.isProduction,
  crossOriginEmbedderPolicy: config.isProduction,
}));

// CORS configuration
app.use(cors({
  origin: config.cors.allowedOrigins,
  credentials: true,
  methods: ['GET', 'POST', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
}));

// Body parsing middleware
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.use(cookieParser());

// Compression middleware
app.use(compression());

// Rate limiting
app.use('/api', apiLimiter);

// Health check endpoint
app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    environment: config.env,
  });
});

// Better Auth routes
// Mount Better Auth handler which will handle all auth endpoints
app.all('/api/auth/*', async (req, res) => {
  try {
    // Better Auth handler expects Web API Request/Response
    // Convert Express req/res to proper format
    const url = `${req.protocol}://${req.get('host')}${req.originalUrl}`;
    
    // Prepare body - Express already parsed it, so convert back to string if present
    let body = undefined;
    if (req.method !== 'GET' && req.method !== 'HEAD' && req.body) {
      body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    }
    
    // Log the request for debugging
    logger.info(`Better Auth request: ${req.method} ${req.originalUrl}`);
    
    const webRequest = new Request(url, {
      method: req.method,
      headers: req.headers as HeadersInit,
      body: body,
    });
    
    const webResponse = await auth.handler(webRequest);
    
    // Log response status
    logger.info(`Better Auth response: ${webResponse.status}`);
    
    // Convert Web Response back to Express response
    res.status(webResponse.status);
    
    // Copy headers
    webResponse.headers.forEach((value, key) => {
      res.setHeader(key, value);
    });
    
    // Get response body
    const responseText = await webResponse.text();
    
    // Try to parse as JSON to validate
    try {
      const jsonData = JSON.parse(responseText);
      res.json(jsonData);
    } catch {
      // If not JSON, send as text
      res.send(responseText);
    }
  } catch (error: any) {
    logger.error('Better Auth handler error:', error);
    console.error('Better Auth handler error:', error);
    
    // Send detailed error in development
    if (config.isDevelopment) {
      res.status(500).json({ 
        error: 'Authentication service error',
        message: error.message,
        stack: error.stack 
      });
    } else {
      res.status(500).json({ error: 'Authentication service error' });
    }
  }
});

// API routes
app.use('/api', routes);

// 404 handler
app.use(notFoundHandler);

// Global error handler (must be last)
app.use(errorHandler);

/**
 * Start the server
 */
const startServer = async () => {
  try {
    // Test database connection
    logger.info('Testing database connection...');
    // Add database connection test here if needed

    // Start listening
    const server = app.listen(config.port, () => {
      logger.info(`🚀 Server started successfully`);
      logger.info(`📦 Environment: ${config.env}`);
      logger.info(`🌐 API URL: ${config.apiUrl}`);
      logger.info(`📝 Health check: ${config.apiUrl}/health`);
      logger.info(`🔐 Auth endpoints: ${config.apiUrl}/api/auth/sign-in/email, /api/auth/sign-up/email`);
    });

    // Graceful shutdown
    const gracefulShutdown = (signal: string) => {
      logger.info(`${signal} received, shutting down gracefully...`);
      server.close(() => {
        logger.info('Server closed');
        process.exit(0);
      });

      // Force shutdown after 10 seconds
      setTimeout(() => {
        logger.error('Forced shutdown after timeout');
        process.exit(1);
      }, 10000);
    };

    process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
    process.on('SIGINT', () => gracefulShutdown('SIGINT'));

  } catch (error) {
    logger.error('Failed to start server:', error);
    process.exit(1);
  }
};

// Start the server
startServer();

export default app;
