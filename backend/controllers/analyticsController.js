import { getAnalyticsSummary } from '../services/analyticsService.js';
import { getDocumentInventory } from '../services/documentService.js';

export async function getAnalytics(req, res, next) {
  try {
    const inventory = await getDocumentInventory();
    const summary = getAnalyticsSummary(inventory);
    res.json(summary);
  } catch (err) {
    next(err);
  }
}
