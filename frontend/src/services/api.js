/**
 * NyayaBench AI - Unified Frontend API Client
 * Interfaces with backend endpoints with robust error handling and SSE streaming support.
 */

const API_BASE = '/api';

/**
 * Health check: Ollama, ChromaDB, and installed models
 */
export async function getHealth() {
  const res = await fetch(`${API_BASE}/health`);
  if (!res.ok) throw new Error(`Health check failed: HTTP ${res.status}`);
  return res.json();
}

/**
 * Model listings and metadata
 */
export async function getModels() {
  const res = await fetch(`${API_BASE}/models`);
  if (!res.ok) throw new Error(`Fetch models failed: HTTP ${res.status}`);
  return res.json();
}

export async function refreshModels() {
  const res = await fetch(`${API_BASE}/models/refresh`, { method: 'POST' });
  if (!res.ok) throw new Error(`Refresh models failed: HTTP ${res.status}`);
  return res.json();
}

/**
 * Ingested Documents
 */
export async function getDocuments() {
  const res = await fetch(`${API_BASE}/documents`);
  if (!res.ok) throw new Error(`Fetch documents failed: HTTP ${res.status}`);
  return res.json();
}

export async function uploadDocuments(fileList) {
  const formData = new FormData();
  for (let i = 0; i < fileList.length; i++) {
    formData.append('files', fileList[i]);
  }

  const res = await fetch(`${API_BASE}/documents/upload`, {
    method: 'POST',
    body: formData
  });

  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Upload failed: HTTP ${res.status}`);
  }
  return data;
}

export async function deleteDocument(docId) {
  const res = await fetch(`${API_BASE}/documents/${encodeURIComponent(docId)}`, {
    method: 'DELETE'
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Delete failed: HTTP ${res.status}`);
  }
  return data;
}

export async function getDocumentPreview(docId) {
  const res = await fetch(`${API_BASE}/documents/${encodeURIComponent(docId)}/preview`);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Preview failed: HTTP ${res.status}`);
  }
  return data;
}

export async function loadSampleDocuments() {
  const res = await fetch(`${API_BASE}/documents/load-sample`, {
    method: 'POST'
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Load samples failed: HTTP ${res.status}`);
  }
  return data;
}

export async function resetCorpus() {
  const res = await fetch(`${API_BASE}/documents/reset`, {
    method: 'DELETE'
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Reset failed: HTTP ${res.status}`);
  }
  return data;
}

/**
 * Single Model Chat (Non-Streaming)
 */
export async function querySingleModel(question, model = 'gemma3:4b', topK = 5, systemPrompt = '') {
  const res = await fetch(`${API_BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, model, topK, systemPrompt })
  });

  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Chat query failed: HTTP ${res.status}`);
  }
  return data;
}

/**
 * Single Model Chat (SSE Streaming)
 * Robust parser supporting CRLF, LF, partial network chunks, and disconnection signals.
 */
export async function streamSingleModel(question, model = 'gemma3:4b', options = {}, onToken, onCitation, signal) {
  const { topK = 5, systemPrompt = '' } = options;
  let reader = null;

  try {
    const res = await fetch(`${API_BASE}/chat/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, model, topK, systemPrompt }),
      signal
    });

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
      throw new Error(errorData.error || `Streaming request failed with status ${res.status}`);
    }

    reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent = 'message';
    let finalResult = null;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop(); // Hold onto partial line

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
          currentEvent = 'message';
          continue;
        }

        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.slice(6).trim();
          continue;
        }

        if (trimmed.startsWith('data:')) {
          const payloadStr = trimmed.slice(5).trim();
          if (!payloadStr) continue;

          try {
            const parsed = JSON.parse(payloadStr);
            const eventType = parsed.type || parsed.event || currentEvent;

            if (eventType === 'token' || parsed.token !== undefined) {
              if (parsed.token && typeof onToken === 'function') {
                onToken(parsed.token);
              }
            } else if (
              eventType === 'context' ||
              eventType === 'citation' ||
              parsed.citations !== undefined
            ) {
              if (typeof onCitation === 'function') {
                onCitation(parsed.citations, parsed.retrieval);
              }
            } else if (
              eventType === 'model_complete' ||
              eventType === 'complete' ||
              eventType === 'all_complete'
            ) {
              finalResult = parsed;
            } else if (eventType === 'model_error' || eventType === 'error') {
              throw new Error(parsed.error || 'Generation error');
            }
          } catch (jsonErr) {
            if (jsonErr.message && !jsonErr.message.includes('Unexpected')) {
              throw jsonErr;
            }
          }
        }
      }
    }

    // Flush any leftover buffer
    if (buffer && buffer.trim().startsWith('data:')) {
      try {
        const payloadStr = buffer.trim().slice(5).trim();
        const parsed = JSON.parse(payloadStr);
        const eventType = parsed.type || parsed.event || currentEvent;
        if (
          eventType === 'model_complete' ||
          eventType === 'complete' ||
          eventType === 'all_complete'
        ) {
          finalResult = parsed;
        }
      } catch {
        // Ignore partial trailing chunk
      }
    }

    return finalResult;
  } catch (err) {
    if (signal?.aborted || err.name === 'AbortError' || (err.message && err.message.toLowerCase().includes('abort'))) {
      return { aborted: true };
    }
    throw err;
  } finally {
    if (reader) {
      try {
        reader.cancel().catch(() => {});
      } catch {
        // Ignore reader cancel error
      }
    }
  }
}

/**
 * Multi-Model Parallel Comparison (Non-Streaming)
 * Retrieval executes exactly ONCE on the backend.
 */
export async function compareModels(question, models, topK = 5, systemPrompt = '') {
  const res = await fetch(`${API_BASE}/compare`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, models, topK, systemPrompt })
  });

  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Comparison failed: HTTP ${res.status}`);
  }
  return data;
}

/**
 * Multi-Model Parallel Comparison (SSE Streaming)
 * Single retrieval + concurrent model streaming with error isolation.
 */
export async function streamCompareModels(question, models, options = {}, onEvent, signal) {
  const { topK = 5, systemPrompt = '' } = options;
  let reader = null;

  try {
    const res = await fetch(`${API_BASE}/compare/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, models, topK, systemPrompt }),
      signal
    });

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({ error: `HTTP ${res.status}` }));
      throw new Error(errorData.error || `Comparison stream failed with status ${res.status}`);
    }

    reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent = 'message';
    let finalResult = null;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split(/\r?\n/);
      buffer = lines.pop();

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) {
          currentEvent = 'message';
          continue;
        }

        if (trimmed.startsWith('event:')) {
          currentEvent = trimmed.slice(6).trim();
          continue;
        }

        if (trimmed.startsWith('data:')) {
          const payloadStr = trimmed.slice(5).trim();
          if (!payloadStr) continue;

          try {
            const parsed = JSON.parse(payloadStr);
            const eventType = parsed.type || parsed.event || currentEvent;

            if (typeof onEvent === 'function') {
              onEvent(eventType, parsed);
            }
            if (eventType === 'all_complete') {
              finalResult = parsed;
            }
          } catch {
            // Ignore incomplete chunk parse
          }
        }
      }
    }

    return finalResult;
  } catch (err) {
    if (signal?.aborted || err.name === 'AbortError' || (err.message && err.message.toLowerCase().includes('abort'))) {
      return { aborted: true };
    }
    throw err;
  } finally {
    if (reader) {
      try {
        reader.cancel().catch(() => {});
      } catch {
        // Ignore
      }
    }
  }
}

/**
 * Analytics
 */
export async function getAnalytics() {
  const res = await fetch(`${API_BASE}/analytics`);
  if (!res.ok) throw new Error(`Fetch analytics failed: HTTP ${res.status}`);
  return res.json();
}

export async function clearAnalytics() {
  const res = await fetch(`${API_BASE}/analytics/clear`, { method: 'POST' });
  if (!res.ok) throw new Error(`Clear analytics failed: HTTP ${res.status}`);
  return res.json();
}
