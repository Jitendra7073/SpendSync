import winston from 'winston';
import { config } from '../config/env';

const { combine, timestamp, printf, colorize, errors } = winston.format;

// Custom log format
const logFormat = printf(({ level, message, timestamp, stack }) => {
  return `${timestamp} [${level}]: ${stack || message}`;
});

// Vercel (and most serverless hosts) mount a read-only filesystem, so
// winston's File transport throws at construction time — which crashes
// every route that imports the logger before it ever handles a request.
// Console output is enough there since Vercel captures stdout/stderr into
// its own log viewer; file transports only make sense on a persistent
// local/VM filesystem.
const isServerless = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);

// Create logger instance
export const logger = winston.createLogger({
  level: config.logging.level,
  format: combine(
    errors({ stack: true }),
    timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
    logFormat
  ),
  transports: [
    // Console transport
    new winston.transports.Console({
      format: combine(colorize(), logFormat),
    }),
    // File transports — local/VM only
    ...(isServerless
      ? []
      : [
          new winston.transports.File({
            filename: 'logs/error.log',
            level: 'error',
          }),
          new winston.transports.File({
            filename: 'logs/combined.log',
          }),
        ]),
  ],
});

// Stream for Morgan HTTP logging middleware
export const stream = {
  write: (message: string) => {
    logger.info(message.trim());
  },
};
