import { config } from '../src/config.js';

const OLLAMA_URL = config.ollama.baseUrl;
const EMBEDDING_MODEL = config.ollama.embeddingModel;

/**
 * Generates an embedding vector for a single text query using nomic-embed-text.
 */
export async function generateQueryEmbedding(text) {
  if (!text || typeof text !== 'string') {
    throw new Error('Valid query string required for embedding generation.');
  }

  try {
    const res = await fetch(`${OLLAMA_URL}/api/embed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: EMBEDDING_MODEL,
        input: text
      })
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      throw new Error(`Embedding request failed (${res.status}): ${errText}`);
    }

    const data = await res.json();
    if (!data.embeddings || !data.embeddings[0]) {
      throw new Error('No embeddings returned by nomic-embed-text.');
    }

    return data.embeddings[0];
  } catch (err) {
    throw new Error(`Embedding generation error: ${err.message}`);
  }
}

/**
 * Generates embeddings in batches of texts for document ingestion.
 */
export async function generateDocumentEmbeddings(texts, batchSize = 10) {
  if (!texts || !Array.isArray(texts) || texts.length === 0) {
    return [];
  }

  const allEmbeddings = [];

  for (let i = 0; i < texts.length; i += batchSize) {
    const batch = texts.slice(i, i + batchSize);
    const res = await fetch(`${OLLAMA_URL}/api/embed`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: EMBEDDING_MODEL,
        input: batch
      })
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      throw new Error(`Batch embedding request failed (${res.status}): ${errText}`);
    }

    const data = await res.json();
    if (!data.embeddings || data.embeddings.length !== batch.length) {
      throw new Error(`Expected ${batch.length} embeddings, received ${data.embeddings?.length}`);
    }

    allEmbeddings.push(...data.embeddings);
  }

  return allEmbeddings;
}
