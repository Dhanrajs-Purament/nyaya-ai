import express from 'express';
import {
  handleChat,
  handleChatStream,
  handleCompare,
  handleCompareStream
} from '../controllers/chatController.js';

const router = express.Router();

router.post('/', handleChat);
router.post('/stream', handleChatStream);
router.post('/compare', handleCompare);
router.post('/compare/stream', handleCompareStream);

export default router;
