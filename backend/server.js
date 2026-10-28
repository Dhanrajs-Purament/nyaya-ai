import express from 'express';
import cors from 'cors';
import { config } from './src/config.js';

// Modular routes
import documentRoutes from './routes/documentRoutes.js';
import chatRoutes from './routes/chatRoutes.js';
import modelRoutes from './routes/modelRoutes.js';
import analyticsRoutes from './routes/analyticsRoutes.js';

// Backward compatibility routes & error handler
import legacyApiRoutes from './src/routes/api.js';
import { errorHandler } from './middleware/errorHandler.js';
import { getHealth } from './controllers/modelController.js';
import { handleCompare, handleCompareStream } from './controllers/chatController.js';

const app = express();

app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'DELETE', 'OPTIONS']
}));

app.use(express.json({ limit: '35mb' }));
app.use(express.urlencoded({ extended: true, limit: '35mb' }));

// Health endpoint
app.get('/api/health', getHealth);

// Section 14 Direct Comparison Endpoints
app.post('/api/compare', handleCompare);
app.post('/api/compare/stream', handleCompareStream);

// Mount Modular Subsystems
app.use('/api/documents', documentRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/models', modelRoutes);
app.use('/api/analytics', analyticsRoutes);

// Mount legacy routes for backward compatibility with previous automated tests
app.use('/api', legacyApiRoutes);

// Root informational endpoint
app.get('/', (req, res) => {
  res.json({
    app: 'NyayaBench AI API',
    tagline: 'One Knowledge Base. Multiple Minds. Evidence-Driven Answers.',
    version: '2.0.0',
    endpoints: {
      health: 'GET /api/health',
      models: 'GET /api/models',
      refreshModels: 'POST /api/models/refresh',
      documents: 'GET /api/documents',
      upload: 'POST /api/documents/upload',
      chat: 'POST /api/chat',
      chatStream: 'POST /api/chat/stream',
      compare: 'POST /api/compare',
      compareStream: 'POST /api/compare/stream',
      analytics: 'GET /api/analytics'
    }
  });
});

// Centralized error handler
app.use(errorHandler);

const PORT = config.port;
app.listen(PORT, () => {
  console.log(`====================================================`);
  console.log(`⚖️  NyayaBench AI Backend active on http://localhost:${PORT}`);
  console.log(`🔗 Ollama Gateway: ${config.ollama.baseUrl}`);
  console.log(`💾 ChromaDB Vector DB: ${config.chroma.host}:${config.chroma.port}`);
  console.log(`🧠 Embedding Engine: ${config.ollama.embeddingModel}`);
  console.log(`⚡ Models: gemma3:1b (1B), gemma3:4b (4B - Default), qwen3:4b (4B)`);
  console.log(`====================================================`);
});
