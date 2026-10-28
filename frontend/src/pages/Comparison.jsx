import React, { useState, useRef } from 'react';
import {
  GitCompare,
  StopCircle,
  Layers
} from 'lucide-react';
import ComparisonPanel from '../components/ComparisonPanel';
import { compareModels, streamCompareModels } from '../services/api';

const BENCHMARK_PRESETS = [
  {
    title: 'Right to Privacy & Puttaswamy',
    query: 'What did the 9-judge bench in Justice K.S. Puttaswamy hold regarding Article 21 and Privacy, and what is the four-fold proportionality test?'
  },
  {
    title: 'Doctrine of Basic Structure',
    query: 'Explain the Basic Structure Doctrine established in Kesavananda Bharati v. State of Kerala and the limits on constitutional amendments under Article 368.'
  },
  {
    title: 'Bharatiya Nyaya Sanhita (BNS) vs IPC',
    query: 'How does the Bharatiya Nyaya Sanhita (BNS) treat Murder (Section 103) and Theft (Section 303) compared to IPC, and where is community service applicable?'
  },
  {
    title: 'Arnesh Kumar Arrest Guidelines',
    query: 'What are the mandatory guidelines issued by the Supreme Court in Arnesh Kumar v. State of Bihar regarding police arrest powers for offenses punishable under 7 years?'
  }
];

export default function Comparison({ initialQuestion = '' }) {
  const [question, setQuestion] = useState(initialQuestion);
  const [isComparing, setIsComparing] = useState(false);
  const [useStreaming, setUseStreaming] = useState(true);

  // Results state
  const [comparisonData, setComparisonData] = useState(null);
  const [retrievedChunks, setRetrievedChunks] = useState([]);
  const [retrievalSummary, setRetrievalSummary] = useState({});

  // Real-time stream state per model
  const [streamStatuses, setStreamStatuses] = useState({
    'gemma3:1b': { streaming: false, text: '' },
    'gemma3:4b': { streaming: false, text: '' },
    'qwen3:4b': { streaming: false, text: '' }
  });

  const abortControllerRef = useRef(null);

  const handleStop = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsComparing(false);
      setStreamStatuses((prev) => ({
        'gemma3:1b': { ...prev['gemma3:1b'], streaming: false },
        'gemma3:4b': { ...prev['gemma3:4b'], streaming: false },
        'qwen3:4b': { ...prev['qwen3:4b'], streaming: false }
      }));
    }
  };

  const runComparison = async (qText = question) => {
    const q = qText.trim();
    if (!q || isComparing) return;

    setIsComparing(true);
    setQuestion(q);
    abortControllerRef.current = new AbortController();

    // Reset streaming indicators
    setStreamStatuses({
      'gemma3:1b': { streaming: true, text: '' },
      'gemma3:4b': { streaming: true, text: '' },
      'qwen3:4b': { streaming: true, text: '' }
    });
    setComparisonData(null);
    setRetrievedChunks([]);

    if (useStreaming) {
      const liveOutputs = {
        'gemma3:1b': '',
        'gemma3:4b': '',
        'qwen3:4b': ''
      };
      const finalModelResults = [];

      try {
        await streamCompareModels(
          q,
          ['gemma3:1b', 'gemma3:4b', 'qwen3:4b'],
          { topK: 5 },
          (eventType, data) => {
            if (eventType === 'context') {
              setRetrievalSummary(data.retrieval || {});
              setRetrievedChunks(data.citations || []);
            } else if (eventType === 'token') {
              const { model, token } = data;
              liveOutputs[model] = (liveOutputs[model] || '') + token;
              setStreamStatuses((prev) => ({
                ...prev,
                [model]: { streaming: true, text: liveOutputs[model] }
              }));
            } else if (eventType === 'model_complete') {
              const { model, fullResponse, metrics, citations } = data;
              finalModelResults.push({
                model,
                success: true,
                response: fullResponse,
                metrics,
                citations
              });
              setStreamStatuses((prev) => ({
                ...prev,
                [model]: { streaming: false, text: fullResponse }
              }));
            } else if (eventType === 'model_error') {
              const { model, error } = data;
              finalModelResults.push({
                model,
                success: false,
                response: '',
                error,
                metrics: { latencySeconds: 0, tokensGenerated: 0, tokensPerSecond: 0 }
              });
              setStreamStatuses((prev) => ({
                ...prev,
                [model]: { streaming: false, text: `Error: ${error}` }
              }));
            } else if (eventType === 'all_complete') {
              setComparisonData({
                question: q,
                results: finalModelResults
              });
            }
          },
          abortControllerRef.current.signal
        );

        setComparisonData({
          question: q,
          results: finalModelResults
        });
      } catch (err) {
        if (!err.message?.includes('aborted')) {
          alert(`Comparison execution error: ${err.message}`);
        }
      } finally {
        setIsComparing(false);
      }
    } else {
      // Non-streaming fallback
      try {
        const result = await compareModels(q, ['gemma3:1b', 'gemma3:4b', 'qwen3:4b'], 5);
        setComparisonData(result);
        setRetrievalSummary(result.retrieval || {});
        setRetrievedChunks(result.retrieval?.chunks || []);
      } catch (err) {
        alert(`Comparison error: ${err.message}`);
      } finally {
        setIsComparing(false);
      }
    }
  };

  return (
    <div className="space-y-8 w-full py-2">
      {/* Header bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-white/10">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight font-sans">
              Parallel Multi-LLM Comparison
            </h1>
            <span className="text-[10px] font-mono px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 font-bold">
              Single-Retrieval Invariant
            </span>
          </div>
          <p className="text-xs sm:text-sm text-slate-400 mt-2 max-w-3xl leading-relaxed">
            All three models receive the identical retrieved legal context from ChromaDB.
            Inference executes in parallel with individual metrics measurement and independent error isolation.
          </p>
        </div>

        <div className="flex items-center gap-3 text-xs font-mono self-start sm:self-center">
          <button
            onClick={() => setUseStreaming(!useStreaming)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#101626] border border-white/10 text-slate-300 hover:text-white cursor-pointer shadow-sm"
          >
            <div className={`w-2.5 h-2.5 rounded-full ${useStreaming ? 'bg-purple-400 shadow-sm shadow-purple-400/50' : 'bg-slate-600'}`} />
            <span>{useStreaming ? 'SSE Streaming' : 'Standard'}</span>
          </button>
        </div>
      </div>

      {/* Preset Questions Selector with Generous Spacing */}
      <div className="space-y-3">
        <span className="text-xs font-mono text-slate-400 uppercase tracking-wider font-semibold">
          Suggested Comparative Benchmark Queries:
        </span>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {BENCHMARK_PRESETS.map((preset, idx) => (
            <button
              key={idx}
              onClick={() => {
                setQuestion(preset.query);
                runComparison(preset.query);
              }}
              disabled={isComparing}
              className="text-left p-4 rounded-2xl border border-white/10 bg-[#101626]/80 hover:bg-[#141D30] hover:border-purple-500/40 transition-all duration-200 text-xs group disabled:opacity-50 shadow-md cursor-pointer flex flex-col justify-between"
            >
              <div className="font-bold text-slate-200 group-hover:text-purple-300 transition-colors text-sm">
                {preset.title}
              </div>
              <div className="text-xs text-slate-400 line-clamp-2 mt-2 leading-relaxed font-sans">
                {preset.query}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Question Input Card - Expansive Full Width */}
      <div className="rounded-3xl border border-white/10 bg-[#101626] p-6 shadow-2xl space-y-4">
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          rows={2}
          placeholder="Enter a legal or analytical question to compare all three models side by side with identical evidence..."
          className="w-full bg-transparent p-2 text-sm text-white placeholder-slate-500 resize-none focus:outline-none min-h-[75px] leading-relaxed"
        />

        <div className="flex items-center justify-between pt-3 border-t border-white/10">
          <div className="flex items-center gap-2 text-xs text-slate-400 font-mono">
            <Layers className="w-4 h-4 text-purple-400" />
            <span>Models: Gemma 3 1B, Gemma 3 4B, Qwen 3 4B</span>
          </div>

          <div className="flex items-center gap-3">
            {isComparing ? (
              <button
                onClick={handleStop}
                className="px-6 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-rose-900/40 transition-all cursor-pointer"
              >
                <StopCircle className="w-4 h-4" />
                <span>Stop Inference</span>
              </button>
            ) : (
              <button
                onClick={() => runComparison()}
                disabled={!question.trim()}
                className="px-7 py-3 rounded-xl bg-gradient-to-r from-amber-500 via-purple-600 to-indigo-600 hover:opacity-90 text-white text-xs font-bold flex items-center gap-2.5 shadow-xl shadow-purple-900/40 transition-all disabled:opacity-40 cursor-pointer"
              >
                <GitCompare className="w-4 h-4" />
                <span>Compare All Models</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Comparison Panels Output */}
      {(comparisonData || isComparing) && (
        <ComparisonPanel
          question={question}
          retrievalSummary={retrievalSummary}
          retrievedChunks={retrievedChunks}
          results={comparisonData?.results || []}
          isStreaming={isComparing}
          streamStatuses={streamStatuses}
        />
      )}
    </div>
  );
}
