import { retrieveContext } from '../services/retrievalService.js';
import { executeGroundedModel, executeGroundedModelStream } from '../services/ragService.js';
import { compareAllModels, compareAllModelsStream } from '../services/comparisonService.js';
import { recordSingleQuery, recordComparisonQuery } from '../services/analyticsService.js';
import { validateQuestion } from '../utils/validators.js';

function sendSSE(res, event, data) {
  if (res.writableEnded || res.destroyed) return;
  const payload = { type: event, event, ...data };
  res.write(`event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`);
  if (typeof res.flush === 'function') {
    res.flush();
  }
}

/**
 * Single-Model Grounded Chat (POST /api/chat)
 */
export async function handleChat(req, res, next) {
  try {
    const question = validateQuestion(req.body.question || req.body.query);
    const model = req.body.model || 'gemma3:4b';
    const topK = parseInt(req.body.topK, 10) || 5;

    // 1. Shared retrieval service
    const retrievalResult = await retrieveContext(question, { topK });

    // 2. Model adapter
    const output = await executeGroundedModel(model, retrievalResult, {
      systemPrompt: req.body.systemPrompt
    });

    // 3. Record telemetry in analytics
    const avgSim = retrievalResult.chunks.length > 0
      ? retrievalResult.chunks.reduce((sum, c) => sum + c.similarity, 0) / retrievalResult.chunks.length
      : 0;

    recordSingleQuery({
      question,
      model,
      latencySeconds: output.metrics.latencySeconds,
      tokensGenerated: output.metrics.tokensGenerated,
      answerLength: (output.response || '').length,
      success: output.success,
      hasValidCitation: output.citations?.validation?.validSources?.length > 0,
      retrievalDurationMs: retrievalResult.durationMs,
      chunksRetrieved: retrievalResult.chunks.length,
      avgSimilarity: avgSim
    });

    res.json({
      answer: output.response,
      citations: output.citations.validation.validSources,
      validation: output.citations.validation,
      retrievedChunks: retrievalResult.chunks,
      model,
      metrics: {
        retrievalTime: retrievalResult.durationMs,
        generationTime: Math.round(output.metrics.latencySeconds * 1000),
        totalTime: output.metrics.totalDurationMs,
        tokensGenerated: output.metrics.tokensGenerated,
        tokensPerSecond: output.metrics.tokensPerSecond
      }
    });
  } catch (err) {
    next(err);
  }
}

/**
 * Single-Model Streaming Chat (POST /api/chat/stream)
 */
export async function handleChatStream(req, res, next) {
  const question = validateQuestion(req.body.question || req.body.query);
  const model = req.body.model || 'gemma3:4b';
  const topK = parseInt(req.body.topK, 10) || 5;

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
    const retrievalResult = await retrieveContext(question, { topK });

    sendSSE(res, 'context', {
      question,
      retrieval: {
        durationMs: retrievalResult.durationMs,
        chunksCount: retrievalResult.chunks.length,
        retrievalRunsCount: 1
      },
      citations: retrievalResult.chunks,
      model
    });

    const output = await executeGroundedModelStream(
      model,
      retrievalResult,
      (token) => {
        sendSSE(res, 'token', { model, token });
      },
      abortController.signal,
      { systemPrompt: req.body.systemPrompt }
    );

    sendSSE(res, 'model_complete', {
      model,
      fullResponse: output.response,
      metrics: output.metrics,
      citations: output.citations
    });

    recordSingleQuery({
      question,
      model,
      latencySeconds: output.metrics?.latencySeconds || 0,
      tokensGenerated: output.metrics?.tokensGenerated || 0,
      answerLength: (output.response || '').length,
      success: true,
      hasValidCitation: output.citations?.validation?.validSources?.length > 0,
      retrievalDurationMs: retrievalResult.durationMs,
      chunksRetrieved: retrievalResult.chunks.length,
      avgSimilarity: 0
    });
  } catch (err) {
    if (!abortController.signal.aborted) {
      sendSSE(res, 'model_error', {
        model,
        error: err.message || 'Stream generation error'
      });
    }
  } finally {
    res.removeListener('close', onClose);
    if (!res.writableEnded) {
      sendSSE(res, 'all_complete', { completedAt: new Date().toISOString() });
      res.end();
    }
  }
}

/**
 * Multi-Model Comparison (POST /api/compare)
 */
export async function handleCompare(req, res, next) {
  try {
    const question = validateQuestion(req.body.question || req.body.query);
    const models = req.body.models;
    const topK = parseInt(req.body.topK, 10) || 5;

    const compData = await compareAllModels(question, models, {
      topK,
      systemPrompt: req.body.systemPrompt
    });

    recordComparisonQuery({
      question,
      retrievalDurationMs: compData.retrieval.durationMs,
      chunksRetrieved: compData.retrieval.chunksCount,
      results: compData.results
    });

    res.json(compData);
  } catch (err) {
    next(err);
  }
}

/**
 * Multi-Model Streaming Comparison (POST /api/compare/stream)
 */
export async function handleCompareStream(req, res, next) {
  const question = validateQuestion(req.body.question || req.body.query);
  const models = req.body.models;
  const topK = parseInt(req.body.topK, 10) || 5;

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
    const compStream = await compareAllModelsStream(
      question,
      models,
      (eventType, eventData) => {
        sendSSE(res, eventType, eventData);
      },
      abortController.signal,
      {
        topK,
        systemPrompt: req.body.systemPrompt
      }
    );

    recordComparisonQuery({
      question,
      retrievalDurationMs: compStream.retrievalResult.durationMs,
      chunksRetrieved: compStream.retrievalResult.chunks.length,
      results: compStream.results.map((r) => r.status === 'fulfilled' ? r.value : { model: 'unknown', success: false })
    });
  } catch (err) {
    if (!abortController.signal.aborted) {
      sendSSE(res, 'stream_error', { error: err.message || 'Stream processing error' });
    }
  } finally {
    res.removeListener('close', onClose);
    if (!res.writableEnded) {
      sendSSE(res, 'all_complete', { completedAt: new Date().toISOString() });
      res.end();
    }
  }
}
