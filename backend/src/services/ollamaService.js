import { config } from '../config.js';

const OLLAMA_URL = config.ollama.baseUrl;

/**
 * Check Ollama connection and list installed models
 */
export async function checkOllamaStatus() {
  try {
    const res = await fetch(`${OLLAMA_URL}/api/tags`);
    if (!res.ok) {
      return { ok: false, error: `Ollama returned status ${res.status}` };
    }
    const data = await res.json();
    const installed = (data.models || []).map((m) => m.name);
    return {
      ok: true,
      models: installed,
      hasEmbeddingModel: installed.some((m) => m.startsWith(config.ollama.embeddingModel))
    };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}

/**
 * Generate embedding vector using nomic-embed-text
 */
export async function generateEmbedding(text) {
  try {
    const res = await fetch(`${OLLAMA_URL}/api/embed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: config.ollama.embeddingModel,
        input: text
      })
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Embedding generation failed (${res.status}): ${errText}`);
    }

    const data = await res.json();
    if (!data.embeddings || !data.embeddings[0]) {
      throw new Error('No embeddings returned by Ollama');
    }

    return data.embeddings[0];
  } catch (err) {
    throw new Error(`Ollama Embedding Error: ${err.message}`);
  }
}

/**
 * Generate embeddings for an array of text chunks
 */
export async function generateBatchEmbeddings(texts, batchSize = 10) {
  const allEmbeddings = [];
  for (let i = 0; i < texts.length; i += batchSize) {
    const batch = texts.slice(i, i + batchSize);
    const res = await fetch(`${OLLAMA_URL}/api/embed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: config.ollama.embeddingModel,
        input: batch
      })
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Batch embedding failed (${res.status}): ${errText}`);
    }

    const data = await res.json();
    if (!data.embeddings || data.embeddings.length !== batch.length) {
      throw new Error(`Expected ${batch.length} embeddings, got ${data.embeddings?.length}`);
    }

    allEmbeddings.push(...data.embeddings);
  }
  return allEmbeddings;
}

/**
 * Run LLM inference on a specific model with timing and token speed benchmarks
 */
export async function generateResponse(model, prompt, systemPrompt = '') {
  const startTime = Date.now();
  try {
    const payload = {
      model,
      prompt,
      system: systemPrompt,
      stream: false,
      options: {
        temperature: 0.2, // Low temperature for high factual accuracy in legal queries
        top_p: 0.9
      }
    };

    const res = await fetch(`${OLLAMA_URL}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const elapsedMs = Date.now() - startTime;
    const latencySeconds = parseFloat((elapsedMs / 1000).toFixed(2));

    if (!res.ok) {
      const errText = await res.text();
      return {
        model,
        success: false,
        error: `Model ${model} error (${res.status}): ${errText}`,
        metrics: {
          latencySeconds,
          tokensGenerated: 0,
          tokensPerSecond: 0,
          promptEvalCount: 0,
          totalDurationSeconds: latencySeconds
        }
      };
    }

    const data = await res.json();
    const tokensGenerated = data.eval_count || 0;
    const evalDurationSeconds = data.eval_duration ? data.eval_duration / 1e9 : latencySeconds;
    const tokensPerSecond = evalDurationSeconds > 0 && tokensGenerated > 0
      ? parseFloat((tokensGenerated / evalDurationSeconds).toFixed(2))
      : 0;

    return {
      model,
      success: true,
      response: data.response || '',
      metrics: {
        latencySeconds,
        tokensGenerated,
        tokensPerSecond,
        promptEvalCount: data.prompt_eval_count || 0,
        totalDurationSeconds: data.total_duration ? parseFloat((data.total_duration / 1e9).toFixed(2)) : latencySeconds
      }
    };
  } catch (err) {
    const latencySeconds = parseFloat(((Date.now() - startTime) / 1000).toFixed(2));
    return {
      model,
      success: false,
      error: err.message,
      metrics: {
        latencySeconds,
        tokensGenerated: 0,
        tokensPerSecond: 0,
        promptEvalCount: 0,
        totalDurationSeconds: latencySeconds
      }
    };
  }
}

/**
 * Run simultaneous comparative inference across multiple models for benchmark
 */
export async function generateMultiModelComparison(models, prompt, systemPrompt = '') {
  const targetModels = models && models.length > 0 ? models : config.ollama.benchmarkModels.map((m) => m.id);
  const promises = targetModels.map((m) => generateResponse(m, prompt, systemPrompt));
  const results = await Promise.allSettled(promises);

  return results.map((result, idx) => {
    if (result.status === 'fulfilled') {
      return result.value;
    }
    return {
      model: targetModels[idx],
      success: false,
      error: result.reason?.message || 'Unknown execution error',
      metrics: { latencySeconds: 0, tokensGenerated: 0, tokensPerSecond: 0 }
    };
  });
}

/**
 * Stream LLM inference token-by-token from Ollama native stream: true API.
 * Propagates AbortSignal, handles line buffering, and captures authentic Ollama metrics.
 */
export async function streamResponse(model, prompt, systemPrompt = '', onToken, signal) {
  const startTime = Date.now();
  let fullResponse = '';
  let finalMetrics = null;

  const payload = {
    model,
    prompt,
    system: systemPrompt,
    stream: true,
    options: {
      temperature: 0.2,
      top_p: 0.9
    }
  };

  const elapsedMs = () => Date.now() - startTime;

  let res;
  try {
    res = await fetch(`${OLLAMA_URL}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal
    });
  } catch (fetchErr) {
    const latencySec = parseFloat((elapsedMs() / 1000).toFixed(2));
    if (signal?.aborted) {
      throw new Error(`Generation aborted by client`);
    }
    throw new Error(`Failed to reach Ollama for model ${model}: ${fetchErr.message}`);
  }

  if (!res.ok) {
    const errText = await res.text().catch(() => 'Unknown error');
    throw new Error(`Model ${model} error (${res.status}): ${errText}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop(); // Keep partial line for next iteration

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        try {
          const parsed = JSON.parse(trimmed);
          if (parsed.response) {
            fullResponse += parsed.response;
            if (typeof onToken === 'function') {
              onToken(parsed.response);
            }
          }
          if (parsed.done) {
            const totalDurationSec = parsed.total_duration ? parsed.total_duration / 1e9 : elapsedMs() / 1000;
            const evalDurationSec = parsed.eval_duration ? parsed.eval_duration / 1e9 : totalDurationSec;
            const evalCount = parsed.eval_count || 0;
            const tokensPerSec = evalDurationSec > 0 && evalCount > 0
              ? parseFloat((evalCount / evalDurationSec).toFixed(2))
              : 0;

            finalMetrics = {
              latencySeconds: parseFloat((elapsedMs() / 1000).toFixed(2)),
              tokensGenerated: evalCount,
              tokensPerSecond: tokensPerSec,
              promptEvalCount: parsed.prompt_eval_count || 0,
              totalDurationSeconds: parseFloat(totalDurationSec.toFixed(2))
            };
          }
        } catch {
          // Ignore partial or non-JSON line
        }
      }
    }

    // Flush any remaining content in buffer
    if (buffer && buffer.trim()) {
      try {
        const parsed = JSON.parse(buffer.trim());
        if (parsed.response) {
          fullResponse += parsed.response;
          if (typeof onToken === 'function') {
            onToken(parsed.response);
          }
        }
      } catch {
        // Ignore
      }
    }

    if (!finalMetrics) {
      const latencySec = parseFloat((elapsedMs() / 1000).toFixed(2));
      finalMetrics = {
        latencySeconds: latencySec,
        tokensGenerated: 0,
        tokensPerSecond: 0,
        promptEvalCount: 0,
        totalDurationSeconds: latencySec
      };
    }

    return {
      model,
      success: true,
      response: fullResponse,
      metrics: finalMetrics
    };
  } finally {
    try {
      reader.cancel();
    } catch {
      // Ignore reader cancellation errors
    }
  }
}

/**
 * Concurrently streams multiple models with independent error isolation.
 * Dispatches onEvent('token' | 'model_complete' | 'model_error', data) as streams progress.
 */
export async function streamMultiModelComparison(models, prompt, systemPrompt = '', onEvent, signal) {
  const targetModels = models && models.length > 0 ? models : config.ollama.benchmarkModels.map((m) => m.id);

  const modelPromises = targetModels.map(async (model) => {
    try {
      const result = await streamResponse(
        model,
        prompt,
        systemPrompt,
        (token) => {
          if (typeof onEvent === 'function') {
            onEvent('token', { model, token });
          }
        },
        signal
      );

      if (typeof onEvent === 'function') {
        onEvent('model_complete', {
          model,
          fullResponse: result.response,
          metrics: result.metrics
        });
      }
      return result;
    } catch (err) {
      const sanitizedError = err.message || 'Model execution error';
      if (typeof onEvent === 'function') {
        onEvent('model_error', {
          model,
          error: sanitizedError,
          metrics: {
            latencySeconds: 0,
            tokensGenerated: 0,
            tokensPerSecond: 0,
            promptEvalCount: 0,
            totalDurationSeconds: 0
          }
        });
      }
      return {
        model,
        success: false,
        error: sanitizedError,
        metrics: { latencySeconds: 0, tokensGenerated: 0, tokensPerSecond: 0 }
      };
    }
  });

  return await Promise.allSettled(modelPromises);
}

