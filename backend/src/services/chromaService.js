import { ChromaClient } from 'chromadb';
import { config } from '../config.js';
import { generateEmbedding, generateBatchEmbeddings } from './ollamaService.js';

// Dummy embedding function to suppress default embedder requirement since we supply embeddings directly
const dummyEmbeddingFunction = {
  generate: async (texts) => texts.map(() => Array(768).fill(0))
};

class ChromaService {
  constructor() {
    this.client = new ChromaClient({
      ssl: false,
      host: config.chroma.host,
      port: config.chroma.port
    });
    this.collectionName = config.chroma.collectionName;
    this.collection = null;
  }

  async getCollection() {
    if (!this.collection) {
      this.collection = await this.client.getOrCreateCollection({
        name: this.collectionName,
        embeddingFunction: dummyEmbeddingFunction,
        metadata: { 'hnsw:space': 'cosine' }
      });
    }
    return this.collection;
  }

  /**
   * Health check for ChromaDB server
   */
  async checkStatus() {
    try {
      const res = await fetch(`http://${config.chroma.host}:${config.chroma.port}/api/v2/heartbeat`);
      if (res.ok) {
        return { ok: true, host: config.chroma.host, port: config.chroma.port };
      }
      return { ok: false, error: `Heartbeat responded with status ${res.status}` };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }

  /**
   * Ingest chunks into ChromaDB with embeddings computed by nomic-embed-text
   */
  async addChunks(chunks) {
    if (!chunks || chunks.length === 0) return { count: 0 };
    const collection = await this.getCollection();

    const texts = chunks.map((c) => c.text);
    const ids = chunks.map((c) => c.id);
    const metadatas = chunks.map((c) => ({
      documentId: String(c.metadata.documentId || ''),
      documentName: String(c.metadata.filename || c.metadata.title || 'Unknown Document'),
      heading: String(c.metadata.heading || ''),
      chunkIndex: Number(c.metadata.chunkIndex || 0),
      pageNumber: Number(c.metadata.pageNumber || 1),
      sourceType: String(c.metadata.sourceType || 'uploaded')
    }));

    // Generate real 768-dim embeddings via nomic-embed-text
    const embeddings = await generateBatchEmbeddings(texts);

    await collection.add({
      ids,
      embeddings,
      documents: texts,
      metadatas
    });

    return { count: chunks.length };
  }

  /**
   * Semantic vector search using query embedding
   */
  async querySimilar(queryText, nResults = 4) {
    const collection = await this.getCollection();
    const queryEmbedding = await generateEmbedding(queryText);

    const results = await collection.query({
      queryEmbeddings: [queryEmbedding],
      nResults
    });

    if (!results || !results.documents || !results.documents[0]) {
      return [];
    }

    const docs = results.documents[0];
    const metadatas = results.metadatas?.[0] || [];
    const distances = results.distances?.[0] || [];
    const ids = results.ids?.[0] || [];

    return docs.map((doc, idx) => {
      // In cosine space, similarity = 1 - distance (or clamped between 0 and 1)
      const distance = distances[idx] !== undefined ? distances[idx] : 1;
      const similarityScore = Math.max(0, Math.min(1, 1 - distance));

      return {
        id: ids[idx],
        text: doc,
        metadata: metadatas[idx] || {},
        distance: parseFloat(distance.toFixed(4)),
        similarity: parseFloat((similarityScore * 100).toFixed(1))
      };
    });
  }

  /**
   * Get collection stats: total chunks, unique document names
   */
  async getStats() {
    try {
      const collection = await this.getCollection();
      const count = await collection.count();
      const sample = await collection.get({
        limit: 1000,
        include: ['metadatas']
      });

      const uniqueDocs = new Map();
      if (sample && sample.metadatas) {
        sample.metadatas.forEach((m) => {
          if (m && m.documentName) {
            const current = uniqueDocs.get(m.documentName) || 0;
            uniqueDocs.set(m.documentName, current + 1);
          }
        });
      }

      const documents = Array.from(uniqueDocs.entries()).map(([name, chunks]) => ({
        name,
        chunks
      }));

      return {
        totalChunks: count,
        collectionName: this.collectionName,
        documents
      };
    } catch (err) {
      return {
        totalChunks: 0,
        collectionName: this.collectionName,
        documents: [],
        error: err.message
      };
    }
  }

  /**
   * Reset the collection
   */
  async resetCollection() {
    try {
      await this.client.deleteCollection({ name: this.collectionName });
      this.collection = null;
      return { success: true };
    } catch (err) {
      return { success: false, error: err.message };
    }
  }
}

export const chromaService = new ChromaService();
