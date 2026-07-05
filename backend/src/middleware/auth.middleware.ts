import { Request, Response, NextFunction } from 'express';
import { auth } from '../config/auth.js';
import { UnauthorizedError } from '../utils/errors.js';

/**
 * Extended Express Request with user information
 */
export interface AuthRequest extends Request {
  user?: {
    id: string;
    email: string;
    emailVerified: boolean;
  };
  session?: {
    id: string;
    expiresAt: Date;
  };
}

/**
 * Authentication middleware
 * Verifies session and attaches user info to request
 */
export const authenticate = async (
  req: AuthRequest,
  _res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    // Get session from Better Auth
    const session = await auth.api.getSession({
      headers: req.headers as any,
    });

    if (!session || !session.user) {
      throw new UnauthorizedError('Invalid or expired session');
    }

    // Attach user and session to request
    req.user = {
      id: session.user.id,
      email: session.user.email,
      emailVerified: session.user.emailVerified,
    };

    req.session = {
      id: session.session.id,
      expiresAt: new Date(session.session.expiresAt),
    };

    next();
  } catch (error) {
    if (error instanceof UnauthorizedError) {
      next(error);
    } else {
      next(new UnauthorizedError('Authentication failed'));
    }
  }
};

/**
 * Optional authentication middleware
 * Attaches user if authenticated, but doesn't require it
 */
export const optionalAuthenticate = async (
  req: AuthRequest,
  _res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    const session = await auth.api.getSession({
      headers: req.headers as any,
    });

    if (session?.user) {
      req.user = {
        id: session.user.id,
        email: session.user.email,
        emailVerified: session.user.emailVerified,
      };

      req.session = {
        id: session.session.id,
        expiresAt: new Date(session.session.expiresAt),
      };
    }

    next();
  } catch {
    // Continue without authentication
    next();
  }
};
