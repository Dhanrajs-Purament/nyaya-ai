import { checkOllamaStatus } from '../services/ollamaService.js';
import { chromaClient } from '../database/chromaClient.js';
import { config } from '../src/config.js';

export const MODEL_METADATA = [
  {
    id: 'gemma3:1b',
    name: 'Gemma 3 (1B)',
    tag: 'Lightweight',
    color: '#F97316', // Orange
    parameterSize: '1B',
    description: 'Lightweight and responsive. Designed for quick answers, simple document queries, and low-latency interactions. May provide less detailed reasoning on complex questions.'
  },
  {
    id: 'gemma3:4b',
    name: 'Gemma 3 (4B)',
    tag: 'Balanced',
    color: '#8B5CF6', // Purple
    parameterSize: '4B',
    isDefault: true,
    description: 'Balanced intelligence and response quality. Suitable for detailed document analysis, summarization, and multi-step questions. Recommended default model.'
  },
  {
    id: 'qwen3:4b',
    name: 'Qwen 3 (4B)',
    tag: 'High-Reasoning',
    color: '#3B82F6', // Blue
    parameterSize: '4B',
    description: 'Reasoning-oriented alternative for analytical questions and structured explanations. Compare its results against Gemma using identical evidence.'
  }
];

export async function getModels(req, res, next) {
  try {
    const status = await checkOllamaStatus();
    const installed = status.models || [];

    const models = MODEL_METADATA.map((m) => {
      const isAvailable = installed.some((inst) => inst.startsWith(m.id));
      return {
        ...m,
        available: isAvailable,
        status: isAvailable ? 'ready' : 'missing',
        statusMessage: isAvailable ? 'Ready for inference' : 'Model not found. Please pull this model using Ollama.'
      };
    });

    res.json({
      models,
      embeddingModel: {
        id: config.ollama.embeddingModel,
        available: status.hasEmbeddingModel,
        status: status.hasEmbeddingModel ? 'ready' : 'missing'
      },
      ollamaReachable: status.ok
    });
  } catch (err) {
    next(err);
  }
}

export async function refreshModels(req, res, next) {
  return getModels(req, res, next);
}

export async function getHealth(req, res, next) {
  try {
    const [ollamaStatus, chromaStatus] = await Promise.all([
      checkOllamaStatus(),
      chromaClient.checkHeartbeat()
    ]);

    const installed = ollamaStatus.models || [];

    res.json({
      status: 'ok',
      timestamp: new Date().toISOString(),
      services: {
        ollama: {
          reachable: ollamaStatus.ok,
          baseUrl: config.ollama.baseUrl,
          hasEmbeddingModel: ollamaStatus.hasEmbeddingModel,
          installedModels: installed
        },
        chroma: {
          reachable: chromaStatus.ok,
          host: config.chroma.host,
          port: config.chroma.port
        }
      },
      models: MODEL_METADATA.map((m) => ({
        id: m.id,
        name: m.name,
        installed: installed.some((inst) => inst.startsWith(m.id))
      })),
      benchmarkModels: MODEL_METADATA.map((m) => ({
        id: m.id,
        name: m.name,
        installed: installed.some((inst) => inst.startsWith(m.id))
      }))
    });
  } catch (err) {
    next(err);
  }
}
