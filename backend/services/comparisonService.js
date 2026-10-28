import { retrieveContext } from './retrievalService.js';
import { executeGroundedModel, executeGroundedModelStream } from './ragService.js';
import { streamMultiModelComparison } from './ollamaService.js';
import { formatEvidenceWithTags, validateCitations } from './citationService.js';
import { buildGroundedPrompt, SYSTEM_PROMPT } from './ragService.js';
import { config } from '../src/config.js';

/**
 * Parallel Multi-Model Comparison Service.
 * Guarantees that retrieval executes exactly ONCE for all models.
 * 
 * @param {string} question The user question
 * @param {Array<string>} models List of models to evaluate (defaults to Gemma 1B, Gemma 4B, Qwen 3 4B)
 * @param {object} options Configuration (topK, systemPrompt)
 */
export async function compareAllModels(question, models, options = {}) {
  const targetModels = models && models.length > 0 ? models : config.ollama.benchmarkModels.map((m) => m.id);

  // 1. SHARED RETRIEVAL: Executed strictly ONCE for the entire comparison
  const retrievalResult = await retrieveContext(question, {
    topK: options.topK || 5
  });

  // 2. Parallel model generation across identical retrieved evidence
  const promises = targetModels.map((model) =>
    executeGroundedModel(model, retrievalResult, { systemPrompt: options.systemPrompt })
  );

  const settledResults = await Promise.allSettled(promises);

  const modelOutputs = settledResults.map((res, idx) => {
    if (res.status === 'fulfilled') {
      return res.value;
    }
    return {
      model: targetModels[idx],
      success: false,
      error: res.reason?.message || 'Model execution error',
      response: '',
      metrics: {
        latencySeconds: 0,
        tokensGenerated: 0,
        tokensPerSecond: 0,
        retrievalDurationMs: retrievalResult.durationMs
      },
      citations: {
        validation: { status: 'failed', validSources: [], invalidSources: [], coveragePercentage: 0 },
        retrievedChunks: retrievalResult.chunks
      }
    };
  });

  return {
    question,
    retrieval: {
      retrievalRunsCount: 1, // Single retrieval invariant explicitly confirmed
      durationMs: retrievalResult.durationMs,
      chunksCount: retrievalResult.chunks.length,
      chunks: retrievalResult.chunks
    },
    results: modelOutputs,
    timestamp: new Date().toISOString()
  };
}

/**
 * Real-time SSE streaming for parallel multi-model comparison.
 */
export async function compareAllModelsStream(question, models, onEvent, signal, options = {}) {
  const targetModels = models && models.length > 0 ? models : config.ollama.benchmarkModels.map((m) => m.id);

  // 1. SHARED RETRIEVAL: Exactly once
  const retrievalResult = await retrieveContext(question, {
    topK: options.topK || 5
  });

  const { taggedText, sourceMap } = formatEvidenceWithTags(retrievalResult.chunks || []);
  const userPrompt = buildGroundedPrompt(question, taggedText);
  const systemPrompt = options.systemPrompt || SYSTEM_PROMPT;

  // 2. Dispatch context immediately
  if (typeof onEvent === 'function') {
    onEvent('context', {
      question,
      retrieval: {
        retrievalRunsCount: 1,
        durationMs: retrievalResult.durationMs,
        chunksCount: retrievalResult.chunks.length
      },
      citations: retrievalResult.chunks,
      models: targetModels
    });
  }

  // 3. Concurrently stream tokens with independent error isolation
  const results = await streamMultiModelComparison(
    targetModels,
    userPrompt,
    systemPrompt,
    (eventType, data) => {
      if (eventType === 'model_complete') {
        const citationValidation = validateCitations(data.fullResponse || '', sourceMap);
        onEvent(eventType, {
          ...data,
          citations: {
            validation: citationValidation,
            retrievedChunks: retrievalResult.chunks
          }
        });
      } else {
        onEvent(eventType, data);
      }
    },
    signal
  );

  return {
    retrievalResult,
    results
  };
}
