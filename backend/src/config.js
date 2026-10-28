import dotenv from 'dotenv';
dotenv.config();

export const config = {
  port: parseInt(process.env.PORT || '5000', 10),
  ollama: {
    baseUrl: process.env.OLLAMA_BASE_URL || 'http://localhost:11434',
    embeddingModel: process.env.EMBEDDING_MODEL || 'nomic-embed-text',
    benchmarkModels: [
      { id: 'gemma3:1b', name: 'Gemma 3 (1B)', tag: 'Lightweight', parameterSize: '1B' },
      { id: 'gemma3:4b', name: 'Gemma 3 (4B)', tag: 'Balanced', parameterSize: '4B' },
      { id: 'qwen3:4b', name: 'Qwen 3 (4B)', tag: 'High-Reasoning', parameterSize: '4B' }
    ]
  },
  chroma: {
    host: process.env.CHROMA_HOST || 'localhost',
    port: parseInt(process.env.CHROMA_PORT || '8000', 10),
    collectionName: process.env.DEFAULT_COLLECTION || 'nyayabench_legal_corpus'
  }
};
