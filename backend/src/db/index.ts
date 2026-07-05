import { drizzle } from 'drizzle-orm/postgres-js';
import postgres from 'postgres';
import * as schema from './schema/index.js';
import { config } from '../config/env.js';

// Create PostgreSQL connection
const queryClient = postgres(config.database.url);

// Create Drizzle instance
export const db = drizzle(queryClient, { schema });

export type Database = typeof db;
