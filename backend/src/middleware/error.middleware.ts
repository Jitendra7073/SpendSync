import { Request, Response, NextFunction } from 'express';
import { ZodError } from 'zod';
import { ApiError } from '../utils/errors.js';
import { sendError } from '../utils/response.js';
import { logger } from '../utils/logger.js';
import { config } from '../config/env.js';

/**
 * Global error handling middleware
 */
export const errorHandler = (
  err: Error,
  req: Request,
  res: Response,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _next: NextFunction
): void => {
  // Log error
  logger.error('Error:', {
    message: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method,
  });

  // Handle Zod validation errors
  if (err instanceof ZodError) {
    sendError(
      res,
      'Validation failed',
      422,
      'VALIDATION_ERROR',
      config.isDevelopment ? err.errors : undefined
    );
    return;
  }

  // Handle custom API errors
  if (err instanceof ApiError) {
    sendError(res, err.message, err.statusCode, err.code, err.details);
    return;
  }

  // Handle unknown errors
  const message = config.isDevelopment ? err.message : 'Internal server error';
  const details = config.isDevelopment ? err.stack : undefined;

  sendError(res, message, 500, 'INTERNAL_ERROR', details);
};

/**
 * 404 Not Found handler
 */
export const notFoundHandler = (req: Request, res: Response): void => {
  sendError(res, `Cannot ${req.method} ${req.path}`, 404, 'ROUTE_NOT_FOUND');
};
