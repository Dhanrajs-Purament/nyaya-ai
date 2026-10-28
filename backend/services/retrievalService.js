import { chromaClient } from '../database/chromaClient.js';
import { generateQueryEmbedding } from './embeddingService.js';
import { config } from '../src/config.js';

/**
 * Shared, Model-Independent RAG Retrieval Service.
 * Generates query embedding via nomic-embed-text, queries ChromaDB, and returns an immutable structured retrieval result.
 * 
 * @param {string} question The user question
 * @param {object} options Configuration options (topK, minSimilarity)
 * @returns {Promise<object>} Structured retrieval result object
 */
export async function retrieveContext(question, options = {}) {
  const { topK = 5, minSimilarity = 0 } = options;
  const startTime = Date.now();

  // 1. Generate query embedding using nomic-embed-text
  const queryEmbedding = await generateQueryEmbedding(question);

  // 2. Query ChromaDB collection
  const collection = await chromaClient.getCollection();
  const rawResults = await collection.query({
    queryEmbeddings: [queryEmbedding],
    nResults: Math.max(1, parseInt(topK, 10) || 5)
  });

  const durationMs = Date.now() - startTime;

  if (!rawResults || !rawResults.documents || !rawResults.documents[0] || rawResults.documents[0].length === 0) {
    return {
      question,
      retrievedAt: new Date().toISOString(),
      embeddingModel: config.ollama.embeddingModel,
      durationMs,
      chunksCount: 0,
      chunks: [],
      insufficientContext: true
    };
  }

  const docs = rawResults.documents[0];
  const metadatas = rawResults.metadatas?.[0] || [];
  const distances = rawResults.distances?.[0] || [];
  const ids = rawResults.ids?.[0] || [];

  const chunks = docs
    .map((docText, idx) => {
      const distance = distances[idx] !== undefined ? distances[idx] : 1;
      const similarity = Math.max(0, Math.min(100, parseFloat(((1 - distance) * 100).toFixed(1))));
      const meta = metadatas[idx] || {};

      return {
        chunkId: ids[idx],
        documentId: meta.document_id || meta.documentId || 'doc_unknown',
        filename: meta.filename || meta.documentName || 'Unknown Document',
        fileType: meta.file_type || meta.sourceType || 'text',
        pageNumber: meta.page_number !== undefined && meta.page_number !== null ? Number(meta.page_number) : (meta.pageNumber ? Number(meta.pageNumber) : null),
        heading: meta.heading || 'Statutory Context',
        text: docText,
        distance: parseFloat(Number(distance).toFixed(4)),
        similarity
      };
    })
    .filter((c) => c.similarity >= minSimilarity);

  return {
    question,
    retrievedAt: new Date().toISOString(),
    embeddingModel: config.ollama.embeddingModel,
    durationMs,
    chunksCount: chunks.length,
    chunks,
    insufficientContext: chunks.length === 0
  };
}
