import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DATA_DIR = path.join(__dirname, '../data');
const ANALYTICS_FILE = path.join(DATA_DIR, 'analytics.json');

if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function loadAnalytics() {
  if (fs.existsSync(ANALYTICS_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(ANALYTICS_FILE, 'utf-8'));
    } catch {
      // Fallback
    }
  }
  return {
    totalQuestions: 0,
    totalComparisons: 0,
    queries: [],
    modelStats: {
      'gemma3:1b': { requests: 0, successful: 0, totalLatency: 0, totalTokens: 0, totalAnswerLength: 0, validCitationsCount: 0 },
      'gemma3:4b': { requests: 0, successful: 0, totalLatency: 0, totalTokens: 0, totalAnswerLength: 0, validCitationsCount: 0 },
      'qwen3:4b': { requests: 0, successful: 0, totalLatency: 0, totalTokens: 0, totalAnswerLength: 0, validCitationsCount: 0 }
    },
    retrievalStats: {
      totalRuns: 0,
      totalDurationMs: 0,
      totalChunksRetrieved: 0,
      totalRelevanceScore: 0,
      insufficientContextCount: 0
    }
  };
}

function saveAnalytics(data) {
  fs.writeFileSync(ANALYTICS_FILE, JSON.stringify(data, null, 2), 'utf-8');
}

/**
 * Records telemetry from a single model query.
 */
export function recordSingleQuery(queryData) {
  const data = loadAnalytics();
  data.totalQuestions++;

  const { model, latencySeconds = 0, tokensGenerated = 0, answerLength = 0, success = true, hasValidCitation = false, retrievalDurationMs = 0, chunksRetrieved = 0, avgSimilarity = 0 } = queryData;

  if (!data.modelStats[model]) {
    data.modelStats[model] = { requests: 0, successful: 0, totalLatency: 0, totalTokens: 0, totalAnswerLength: 0, validCitationsCount: 0 };
  }

  const mStats = data.modelStats[model];
  mStats.requests++;
  if (success) mStats.successful++;
  mStats.totalLatency += Number(latencySeconds) || 0;
  mStats.totalTokens += Number(tokensGenerated) || 0;
  mStats.totalAnswerLength += Number(answerLength) || 0;
  if (hasValidCitation) mStats.validCitationsCount++;

  // Update retrieval stats
  data.retrievalStats.totalRuns++;
  data.retrievalStats.totalDurationMs += Number(retrievalDurationMs) || 0;
  data.retrievalStats.totalChunksRetrieved += Number(chunksRetrieved) || 0;
  data.retrievalStats.totalRelevanceScore += Number(avgSimilarity) || 0;
  if (chunksRetrieved === 0) data.retrievalStats.insufficientContextCount++;

  // Record query snippet (keep last 50)
  data.queries.unshift({
    timestamp: new Date().toISOString(),
    question: queryData.question,
    model,
    latencySeconds,
    tokensGenerated,
    success
  });
  if (data.queries.length > 50) data.queries.pop();

  saveAnalytics(data);
}

/**
 * Records telemetry from a comparison request.
 */
export function recordComparisonQuery(compData) {
  const data = loadAnalytics();
  data.totalQuestions++;
  data.totalComparisons++;

  const { question, retrievalDurationMs = 0, chunksRetrieved = 0, results = [] } = compData;

  data.retrievalStats.totalRuns++;
  data.retrievalStats.totalDurationMs += Number(retrievalDurationMs) || 0;
  data.retrievalStats.totalChunksRetrieved += Number(chunksRetrieved) || 0;
  if (chunksRetrieved === 0) data.retrievalStats.insufficientContextCount++;

  results.forEach((r) => {
    const model = r.model;
    if (!data.modelStats[model]) {
      data.modelStats[model] = { requests: 0, successful: 0, totalLatency: 0, totalTokens: 0, totalAnswerLength: 0, validCitationsCount: 0 };
    }
    const mStats = data.modelStats[model];
    mStats.requests++;
    if (r.success) mStats.successful++;
    mStats.totalLatency += Number(r.metrics?.latencySeconds) || 0;
    mStats.totalTokens += Number(r.metrics?.tokensGenerated) || 0;
    mStats.totalAnswerLength += (r.response || '').length;
    if (r.citations?.validation?.status === 'valid') mStats.validCitationsCount++;
  });

  saveAnalytics(data);
}

/**
 * Returns formatted analytics for the Recharts dashboard.
 */
export function getAnalyticsSummary(documentInventory = {}) {
  const raw = loadAnalytics();

  // Model comparison summary for bar charts
  const modelPerformance = Object.entries(raw.modelStats).map(([modelId, s]) => {
    const avgLatency = s.requests > 0 ? parseFloat((s.totalLatency / s.requests).toFixed(2)) : 0;
    const avgTokens = s.requests > 0 ? Math.round(s.totalTokens / s.requests) : 0;
    const avgLength = s.requests > 0 ? Math.round(s.totalAnswerLength / s.requests) : 0;
    const successRate = s.requests > 0 ? parseFloat(((s.successful / s.requests) * 100).toFixed(1)) : 100;
    const citationRate = s.requests > 0 ? parseFloat(((s.validCitationsCount / s.requests) * 100).toFixed(1)) : 0;

    return {
      modelId,
      name: modelId === 'gemma3:1b' ? 'Gemma 3 (1B)' : modelId === 'gemma3:4b' ? 'Gemma 3 (4B)' : 'Qwen 3 (4B)',
      requests: s.requests,
      avgLatencySeconds: avgLatency,
      avgTokensGenerated: avgTokens,
      avgAnswerLength: avgLength,
      successRatePercentage: successRate,
      citationValidationRate: citationRate
    };
  });

  const retRuns = raw.retrievalStats.totalRuns || 0;
  const avgRetrievalMs = retRuns > 0 ? Math.round(raw.retrievalStats.totalDurationMs / retRuns) : 0;
  const avgChunks = retRuns > 0 ? parseFloat((raw.retrievalStats.totalChunksRetrieved / retRuns).toFixed(1)) : 0;
  const avgRelevance = retRuns > 0 ? parseFloat((raw.retrievalStats.totalRelevanceScore / retRuns).toFixed(1)) : 0;

  return {
    usage: {
      totalQuestionsAsked: raw.totalQuestions,
      totalComparisonRequests: raw.totalComparisons,
      totalDocumentsUploaded: documentInventory.totalDocuments || 0,
      totalChunksIndexed: documentInventory.totalChunks || 0,
      totalBytesIndexed: documentInventory.totalBytesIndexed || 0
    },
    retrieval: {
      totalRetrievalRuns: retRuns,
      avgRetrievalDurationMs: avgRetrievalMs,
      avgChunksRetrieved: avgChunks,
      avgRelevancePercentage: avgRelevance,
      insufficientContextCount: raw.retrievalStats.insufficientContextCount
    },
    modelPerformance,
    recentQueries: raw.queries || []
  };
}
