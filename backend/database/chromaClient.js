import { ChromaClient } from 'chromadb';
import { config } from '../src/config.js';

// Dummy embedding function so ChromaClient doesn't require @chroma-core/default-embed
const dummyEmbeddingFunction = {
  generate: async (texts) => texts.map(() => Array(768).fill(0))
};

class ChromaDBClient {
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

  async checkHeartbeat() {
    try {
      const res = await fetch(`http://${config.chroma.host}:${config.chroma.port}/api/v2/heartbeat`);
      return { ok: res.ok, status: res.status };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }

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

export const chromaClient = new ChromaDBClient();
