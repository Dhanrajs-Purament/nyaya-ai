import express from 'express';
import { uploadMiddleware } from '../middleware/uploadMiddleware.js';
import {
  uploadDocuments,
  listDocuments,
  deleteDocument,
  previewDocument,
  loadSampleCorpus,
  resetCorpus
} from '../controllers/documentController.js';

const router = express.Router();

router.post('/upload', uploadMiddleware.array('files', 15), uploadDocuments);
router.get('/', listDocuments);
router.post('/load-sample', loadSampleCorpus);
router.delete('/reset', resetCorpus);
router.get('/:id/preview', previewDocument);
router.delete('/:id', deleteDocument);

export default router;
