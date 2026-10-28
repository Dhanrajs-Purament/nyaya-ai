import express from 'express';
import multer from 'multer';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { config } from '../config.js';
import {
  checkOllamaStatus,
  generateResponse,
  generateMultiModelComparison,
  streamResponse,
  streamMultiModelComparison
} from '../services/ollamaService.js';
import { chromaService } from '../services/chromaService.js';
import { extractText, chunkLegalDocument } from '../services/documentParser.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const router = express.Router();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 25 * 1024 * 1024 } // 25MB max
});

/**
 * Legal prompt builder for grounded Indian legal RAG responses
 */
function buildLegalPrompt(query, retrievedChunks) {
  const contextBlock = retrievedChunks.length > 0
    ? retrievedChunks
        .map(
          (c, idx) =>
            `[CITATION ${idx + 1}] Source: ${c.metadata?.documentName || 'Document'} (Page ${c.metadata?.pageNumber || 1}) | Heading: ${c.metadata?.heading || 'General Legal Context'}\n${c.text}`
        )
        .join('\n\n---\n\n')
    : 'No relevant legal provisions retrieved from database.';

  return `You are NyayaBench AI, a specialist Indian Legal Intelligence and Statutory Analysis assistant.
Your task is to answer the query strictly grounded in the Indian Legal Context and citations provided below.

INSTRUCTIONS:
1. Ground your response in the provided legal context, citing specific Articles, Sections, Clauses, and landmark judgment ratios where available.
2. Structure your response with clear headings: Legal Provision / Ratio, Analysis, and Conclusion.
3. If the provided context does not have enough information to answer definitively, state what is known and clarify what is missing.
4. Maintain formal judicial precision.

LEGAL CONTEXT:
${contextBlock}

USER QUERY:
${query}

DETAILED LEGAL ANALYSIS & CITATIONS:`;
}

/**
 * GET /api/health - System and model health check
 */
router.get('/health', async (req, res) => {
  try {
    const [ollamaStatus, chromaStatus, dbStats] = await Promise.all([
      checkOllamaStatus(),
      chromaService.checkStatus(),
      chromaService.getStats()
    ]);

    const installedModels = ollamaStatus.models || [];
    const modelsAvailability = config.ollama.benchmarkModels.map((m) => ({
      ...m,
      installed: installedModels.some((name) => name.startsWith(m.id))
    }));

    res.json({
      status: 'ok',
      timestamp: new Date().toISOString(),
      services: {
        ollama: {
          reachable: ollamaStatus.ok,
          baseUrl: config.ollama.baseUrl,
          models: installedModels,
          hasEmbeddingModel: installedModels.some((m) => m.startsWith(config.ollama.embeddingModel))
        },
        chroma: {
          reachable: chromaStatus.ok,
          host: config.chroma.host,
          port: config.chroma.port,
          stats: dbStats
        }
      },
      benchmarkModels: modelsAvailability
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/documents/upload - Upload and ingest PDF/TXT files
 */
router.post('/documents/upload', upload.array('files', 10), async (req, res) => {
  try {
    if (!req.files || req.files.length === 0) {
      return res.status(400).json({ error: 'No files provided. Upload PDF or TXT files.' });
    }

    let totalChunksIngested = 0;
    const processedFiles = [];

    for (const file of req.files) {
      const { text, numPages } = await extractText(file.buffer, file.mimetype, file.originalname);
      const docId = `doc-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
      const chunks = chunkLegalDocument(text, {
        documentId: docId,
        filename: file.originalname,
        sourceType: file.mimetype.includes('pdf') ? 'pdf' : 'txt',
        numPages,
        title: file.originalname.replace(/\.[^/.]+$/, '')
      });

      if (chunks.length > 0) {
        await chromaService.addChunks(chunks);
        totalChunksIngested += chunks.length;
      }

      processedFiles.push({
        filename: file.originalname,
        sizeBytes: file.size,
        numPages,
        chunksGenerated: chunks.length
      });
    }

    const updatedStats = await chromaService.getStats();

    res.json({
      success: true,
      message: `Successfully processed ${processedFiles.length} file(s) into ${totalChunksIngested} chunks.`,
      files: processedFiles,
      totalChunksIngested,
      stats: updatedStats
    });
  } catch (err) {
    console.error('Upload Error:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/documents/load-sample - Ingest bundled Indian Legal sample corpus
 */
router.post('/documents/load-sample', async (req, res) => {
  try {
    const samplesDir = path.join(__dirname, '../../sample_docs');
    if (!fs.existsSync(samplesDir)) {
      return res.status(404).json({ error: 'Sample documents directory not found' });
    }

    const files = fs.readdirSync(samplesDir).filter((f) => f.endsWith('.txt'));
    let totalChunks = 0;
    const loadedFiles = [];

    for (const file of files) {
      const filePath = path.join(samplesDir, file);
      const content = fs.readFileSync(filePath, 'utf-8');
      const docId = `sample-${file.replace(/\.txt$/, '')}`;

      const chunks = chunkLegalDocument(content, {
        documentId: docId,
        filename: file,
        sourceType: 'sample_txt',
        title: file.replace(/_/g, ' ').replace(/\.txt$/, '').toUpperCase()
      });

      if (chunks.length > 0) {
        await chromaService.addChunks(chunks);
        totalChunks += chunks.length;
      }

      loadedFiles.push({ filename: file, chunks: chunks.length });
    }

    const updatedStats = await chromaService.getStats();

    res.json({
      success: true,
      message: `Loaded ${loadedFiles.length} Indian Legal sample documents (${totalChunks} chunks) into ChromaDB.`,
      files: loadedFiles,
      totalChunks,
      stats: updatedStats
    });
  } catch (err) {
    console.error('Load Sample Error:', err);
    res.status(500).json({ error: err.message });
  }
});

/**
 * GET /api/documents - Get collection statistics and list of documents
 */
router.get('/documents', async (req, res) => {
  try {
    const stats = await chromaService.getStats();
    res.json(stats);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * DELETE /api/documents/reset - Reset ChromaDB collection
 */
router.delete('/documents/reset', async (req, res) => {
  try {
    const result = await chromaService.resetCollection();
    res.json({ success: true, message: 'Legal corpus collection reset successfully', result });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/search - Semantic vector retrieval without LLM generation
 */
router.post('/search', async (req, res) => {
  try {
    const { query, topK = 4 } = req.body;
    if (!query) {
      return res.status(400).json({ error: 'Query parameter is required' });
    }

    const results = await chromaService.querySimilar(query, parseInt(topK, 10));
    res.json({
      query,
      resultsCount: results.length,
      results
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/query/single - Single model RAG query
 */
router.post('/query/single', async (req, res) => {
  try {
    const { model = 'gemma3:4b', query, topK = 4, systemPrompt } = req.body;
    if (!query) {
      return res.status(400).json({ error: 'Query parameter is required' });
    }

    // Retrieve context from ChromaDB
    const citations = await chromaService.querySimilar(query, parseInt(topK, 10));
    const fullPrompt = buildLegalPrompt(query, citations);

    const generation = await generateResponse(model, fullPrompt, systemPrompt);

    res.json({
      query,
      model,
      generation,
      citations
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * POST /api/query/compare - Multi-LLM Benchmark Matrix
 * Simultaneously queries Gemma 3 (1B), Gemma 3 (4B), and Qwen 3 (4B) against identical context
 */
router.post('/query/compare', async (req, res) => {
  try {
    const {
      models = ['gemma3:1b', 'gemma3:4b', 'qwen3:4b'],
      query,
      topK = 4,
      systemPrompt
    } = req.body;

    if (!query) {
      return res.status(400).json({ error: 'Query parameter is required' });
    }

    // 1. Single retrieval for perfectly fair comparison across all models
    const retrievalStart = Date.now();
    const citations = await chromaService.querySimilar(query, parseInt(topK, 10));
    const retrievalDurationMs = Date.now() - retrievalStart;

    // 2. Build identical grounded prompt
    const fullPrompt = buildLegalPrompt(query, citations);

    // 3. Multi-LLM inference
    const benchmarkResults = await generateMultiModelComparison(models, fullPrompt, systemPrompt);

    res.json({
      query,
      retrieval: {
        durationMs: retrievalDurationMs,
        citationsCount: citations.length,
        retrievalRunsCount: 1, // Single retrieval invariant
        citations
      },
      results: benchmarkResults
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

/**
 * SSE transmission helper
 */
function sendSSE(res, event, data) {
  if (res.writableEnded || res.destroyed) return;
  const payload = { type: event, event, ...data };
  res.write(`event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`);
  if (typeof res.flush === 'function') {
    res.flush();
  }
}

/**
 * POST /api/query/single/stream - Real-time SSE streaming for single model
 */
router.post('/query/single/stream', async (req, res) => {
  const { model = 'gemma3:4b', query, topK = 4, systemPrompt } = req.body;

  if (!query) {
    return res.status(400).json({ error: 'Query parameter is required' });
  }

  // Set SSE response headers
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no'
  });

  const abortController = new AbortController();

  const onClose = () => {
    if (!res.writableEnded) {
      abortController.abort();
    }
  };
  res.on('close', onClose);

  try {
    // 1. Vector retrieval
    const citations = await chromaService.querySimilar(query, parseInt(topK, 10));
    const fullPrompt = buildLegalPrompt(query, citations);

    // 2. Transmit context immediately
    sendSSE(res, 'context', {
      model,
      query,
      citations
    });

    // 3. Stream model tokens
    const result = await streamResponse(
      model,
      fullPrompt,
      systemPrompt,
      (token) => {
        sendSSE(res, 'token', { model, token });
      },
      abortController.signal
    );

    sendSSE(res, 'model_complete', {
      model,
      fullResponse: result.response,
      metrics: result.metrics
    });
  } catch (err) {
    if (!abortController.signal.aborted) {
      sendSSE(res, 'model_error', {
        model,
        error: err.message || 'Model stream generation failed',
        metrics: { latencySeconds: 0, tokensGenerated: 0, tokensPerSecond: 0 }
      });
    }
  } finally {
    res.removeListener('close', onClose);
    if (!res.writableEnded) {
      sendSSE(res, 'all_complete', {
        completedAt: new Date().toISOString()
      });
      res.end();
    }
  }
});

/**
 * POST /api/query/compare/stream - Real-time SSE streaming for multi-LLM benchmark
 * Preserves the retrieval-once invariant and streams tokens with independent error isolation.
 */
router.post('/query/compare/stream', async (req, res) => {
  const {
    models = ['gemma3:1b', 'gemma3:4b', 'qwen3:4b'],
    query,
    topK = 4,
    systemPrompt
  } = req.body;

  if (!query) {
    return res.status(400).json({ error: 'Query parameter is required' });
  }

  // Set SSE response headers
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no'
  });

  const abortController = new AbortController();

  const onClose = () => {
    if (!res.writableEnded) {
      abortController.abort();
    }
  };
  res.on('close', onClose);

  try {
    // 1. Single retrieval for perfectly fair comparison across all models
    const retrievalStart = Date.now();
    const citations = await chromaService.querySimilar(query, parseInt(topK, 10));
    const retrievalDurationMs = Date.now() - retrievalStart;

    // 2. Dispatch context event immediately before inference begins
    sendSSE(res, 'context', {
      query,
      retrieval: {
        durationMs: retrievalDurationMs,
        citationsCount: citations.length,
        retrievalRunsCount: 1, // Proof of single retrieval invariant
        citations
      },
      models
    });

    // 3. Build identical grounded prompt
    const fullPrompt = buildLegalPrompt(query, citations);

    // 4. Concurrently stream all selected models with error isolation
    await streamMultiModelComparison(
      models,
      fullPrompt,
      systemPrompt,
      (eventType, eventData) => {
        sendSSE(res, eventType, eventData);
      },
      abortController.signal
    );
  } catch (err) {
    if (!abortController.signal.aborted) {
      sendSSE(res, 'stream_error', {
        error: err.message || 'Stream processing error'
      });
    }
  } finally {
    res.removeListener('close', onClose);
    if (!res.writableEnded) {
      sendSSE(res, 'all_complete', {
        totalModels: models.length,
        completedAt: new Date().toISOString()
      });
      res.end();
    }
  }
});

export default router;
