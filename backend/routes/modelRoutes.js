import express from 'express';
import { getModels, refreshModels, getHealth } from '../controllers/modelController.js';

const router = express.Router();

router.get('/', getModels);
router.post('/refresh', refreshModels);
router.get('/health', getHealth);

export default router;
