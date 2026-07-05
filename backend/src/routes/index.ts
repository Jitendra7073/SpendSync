import { Router } from 'express';
import transactionRoutes from './transaction.routes.js';
import categoryRoutes from './category.routes.js';
import budgetRoutes from './budget.routes.js';
import dashboardRoutes from './dashboard.routes.js';
import settingsRoutes from './settings.routes.js';

const router = Router();

// Mount routes
router.use('/transactions', transactionRoutes);
router.use('/categories', categoryRoutes);
router.use('/budgets', budgetRoutes);
router.use('/dashboard', dashboardRoutes);
router.use('/settings', settingsRoutes);

export default router;
