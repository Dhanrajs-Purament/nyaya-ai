import React, { useState } from 'react';
import {
  Copy,
  Check,
  Download,
  Clock,
  Zap,
  CheckCircle2,
  AlertCircle,
  FileText,
  Info,
  Sparkles,
  Layers,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import CitationCard from './CitationCard';

const MODEL_THEMES = {
  'gemma3:1b': {
    name: 'Gemma 3 (1B)',
    tag: 'Lightweight',
    color: '#F97316',
    border: 'border-orange-500/40',
    headerBg: 'bg-orange-950/20',
    badge: 'bg-orange-500/10 text-orange-400 border-orange-500/30'
  },
  'gemma3:4b': {
    name: 'Gemma 3 (4B)',
    tag: 'Balanced Intelligence (Default)',
    color: '#8B5CF6',
    border: 'border-purple-500/40',
    headerBg: 'bg-purple-950/20',
    badge: 'bg-purple-500/10 text-purple-400 border-purple-500/30'
  },
  'qwen3:4b': {
    name: 'Qwen 3 (4B)',
    tag: 'High-Reasoning',
    color: '#3B82F6',
    border: 'border-blue-500/40',
    headerBg: 'bg-blue-950/20',
    badge: 'bg-blue-500/10 text-blue-400 border-blue-500/30'
  }
};

export default function ComparisonPanel({
  question,
  retrievalSummary = {},
  retrievedChunks = [],
  results = [],
  isStreaming = false,
  streamStatuses = {}
}) {
  const [copiedModel, setCopiedModel] = useState(null);
  const [contextExpanded, setContextExpanded] = useState(false);

  const handleCopy = (text, modelId) => {
    navigator.clipboard.writeText(text);
    setCopiedModel(modelId);
    setTimeout(() => setCopiedModel(null), 2000);
  };

  const exportAsMarkdown = () => {
    let md = `# NyayaBench AI — Multi-LLM RAG Comparison Report\n\n`;
    md += `**Question:** ${question}\n`;
    md += `**Timestamp:** ${new Date().toISOString()}\n`;
    md += `**Retrieved Evidence:** ${retrievalSummary.chunksCount || retrievedChunks.length} chunks (Single retrieval pass: ${retrievalSummary.durationMs || 0}ms)\n\n`;
    md += `---\n\n`;

    results.forEach((r) => {
      const theme = MODEL_THEMES[r.model] || { name: r.model };
      md += `### ${theme.name}\n`;
      md += `*Latency: ${r.metrics?.latencySeconds || 0}s | Speed: ${r.metrics?.tokensPerSecond || 0} t/s | Tokens: ${r.metrics?.tokensGenerated || 0}*\n\n`;
      md += `${r.response || (r.error ? `Error: ${r.error}` : 'No response')}\n\n`;
    });

    md += `---\n\n*Note: All models received identical retrieved context. Evaluate answers against supporting citations.*\n`;

    const blob = new Blob([md], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `nyayabench-comparison-${Date.now()}.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const exportAsJSON = () => {
    const data = {
      question,
      timestamp: new Date().toISOString(),
      retrieval: retrievalSummary,
      retrievedChunks,
      comparisonResults: results
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `nyayabench-comparison-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="w-full space-y-8">
      {/* Shared Question & Single-Retrieval Context Bar */}
      <div className="rounded-3xl border border-white/10 bg-[#101626]/90 p-6 sm:p-7 shadow-2xl space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-white/10">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2.5">
              <span className="text-[10px] font-mono uppercase tracking-widest text-purple-400 bg-purple-500/10 px-2.5 py-0.5 rounded-full border border-purple-500/20 font-bold">
                Shared Question
              </span>
              <span className="text-xs font-mono text-emerald-400 bg-emerald-500/10 px-2.5 py-0.5 rounded-full border border-emerald-500/20 flex items-center gap-1.5 font-semibold">
                <CheckCircle2 className="w-3.5 h-3.5" /> Retrieval Invariant: Exactly 1 Pass
              </span>
            </div>
            <h2 className="text-lg sm:text-xl font-bold text-white tracking-tight">{question}</h2>
          </div>

          {/* Export Actions */}
          <div className="flex items-center gap-2.5 flex-shrink-0">
            <button
              onClick={exportAsMarkdown}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-medium text-slate-300 transition-colors cursor-pointer"
            >
              <Download className="w-4 h-4 text-purple-400" />
              <span>Export MD</span>
            </button>
            <button
              onClick={exportAsJSON}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-xs font-medium text-slate-300 transition-colors cursor-pointer"
            >
              <Download className="w-4 h-4 text-blue-400" />
              <span>Export JSON</span>
            </button>
          </div>
        </div>

        {/* Collapsible Shared Evidence Drawer */}
        <div className="pt-1">
          <div
            onClick={() => setContextExpanded(!contextExpanded)}
            className="flex items-center justify-between cursor-pointer py-1.5 text-xs text-slate-300 hover:text-white"
          >
            <div className="flex items-center gap-2.5">
              <Layers className="w-4 h-4 text-amber-400" />
              <span className="font-semibold text-sm">
                Shared Retrieved Evidence ({retrievalSummary.chunksCount || retrievedChunks.length} chunks, {retrievalSummary.durationMs || 0}ms)
              </span>
            </div>
            {contextExpanded ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
          </div>

          {contextExpanded && (
            <div className="mt-4 space-y-3 pt-3 border-t border-white/10 max-h-80 overflow-y-auto">
              {retrievedChunks.map((chunk, idx) => (
                <CitationCard key={chunk.chunkId || idx} citation={chunk} index={idx} />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Three Model Response Panels Side-by-Side (Stacked on Mobile) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 w-full">
        {['gemma3:1b', 'gemma3:4b', 'qwen3:4b'].map((modelId) => {
          const theme = MODEL_THEMES[modelId];
          const result = results.find((r) => r.model === modelId);
          const isModelStreaming = streamStatuses[modelId]?.streaming;
          const streamText = streamStatuses[modelId]?.text;
          const displayText = result?.response || streamText || '';
          const hasError = result?.error;
          const isCopied = copiedModel === modelId;

          const latency = result?.metrics?.latencySeconds;
          const tps = result?.metrics?.tokensPerSecond;
          const tokens = result?.metrics?.tokensGenerated;

          return (
            <div
              key={modelId}
              className={`rounded-3xl border ${theme.border} bg-[#101626]/90 flex flex-col shadow-2xl overflow-hidden`}
            >
              {/* Panel Header */}
              <div className={`p-5 border-b border-white/10 ${theme.headerBg} flex items-center justify-between`}>
                <div className="flex items-center gap-3">
                  <div
                    className="w-3.5 h-3.5 rounded-full shadow-sm"
                    style={{ backgroundColor: theme.color }}
                  />
                  <div>
                    <h3 className="text-base font-bold text-white tracking-tight">{theme.name}</h3>
                    <span className={`text-[10px] font-mono font-semibold px-2 py-0.5 rounded-full border ${theme.badge}`}>
                      {theme.tag}
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  {displayText && (
                    <button
                      onClick={() => handleCopy(displayText, modelId)}
                      className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                      title="Copy Answer"
                    >
                      {isCopied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    </button>
                  )}
                </div>
              </div>

              {/* Response Body with Expansive Space */}
              <div className="p-6 flex-1 flex flex-col justify-between overflow-y-auto min-h-[400px] max-h-[600px]">
                <div>
                  {isModelStreaming && !displayText ? (
                    <div className="flex items-center gap-3 text-xs text-slate-400 py-12 justify-center animate-pulse">
                      <Sparkles className="w-5 h-5 text-purple-400 animate-spin" />
                      <span>Generating grounded reasoning...</span>
                    </div>
                  ) : hasError ? (
                    <div className="p-4 rounded-2xl bg-rose-950/20 border border-rose-500/30 text-rose-300 text-xs flex items-start gap-3">
                      <AlertCircle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
                      <div>
                        <div className="font-bold">Generation Error</div>
                        <div className="text-xs mt-1 text-rose-400/90 leading-relaxed">{result.error}</div>
                      </div>
                    </div>
                  ) : displayText ? (
                    <div className="text-sm text-slate-200 leading-relaxed font-sans whitespace-pre-wrap select-text space-y-3">
                      {displayText}
                      {isModelStreaming && (
                        <span className="inline-block w-2.5 h-4 bg-purple-400 animate-pulse ml-1.5 translate-y-0.5 rounded-sm" />
                      )}
                    </div>
                  ) : (
                    <div className="text-center py-20 text-slate-500 text-xs">
                      Awaiting comparison trigger...
                    </div>
                  )}
                </div>

                {/* Citations Coverage inside Panel */}
                {result?.citations?.validation && (
                  <div className="mt-6 pt-4 border-t border-white/5 space-y-2">
                    <div className="flex items-center justify-between text-xs font-mono text-slate-400">
                      <span>Citation Coverage:</span>
                      <span className="text-emerald-400 font-bold">
                        {result.citations.validation.coveragePercentage || 100}%
                      </span>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {(result.citations.validation.validSources || []).map((s, i) => (
                        <span
                          key={i}
                          className="text-[11px] font-mono px-2 py-0.5 rounded-md bg-emerald-500/10 border border-emerald-500/20 text-emerald-300"
                        >
                          [{s.tag}] {s.filename} (p.{s.pageNumber ?? '1'})
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Panel Footer Metrics */}
              <div className="px-5 py-4 bg-[#080B14]/80 border-t border-white/10 flex items-center justify-between text-xs font-mono text-slate-400">
                <div className="flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-amber-400" />
                  <span>{latency !== undefined ? `${latency}s` : '—'}</span>
                </div>

                <div className="flex items-center gap-1.5">
                  <Zap className="w-3.5 h-3.5 text-purple-400" />
                  <span>{tps ? `${tps} t/s` : '—'}</span>
                </div>

                <div className="text-slate-500">
                  {tokens ? `${tokens} tokens` : '—'}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Educational Note with Comfortable Spacing */}
      <div className="rounded-2xl border border-white/10 bg-[#101626]/70 p-5 flex items-start gap-4 text-xs sm:text-sm text-slate-400 shadow-md">
        <Info className="w-5 h-5 text-purple-400 flex-shrink-0 mt-0.5" />
        <div className="leading-relaxed">
          <span className="font-bold text-slate-200">Methodological Note: </span>
          Different answers do not necessarily mean one model is correct. All models received the same retrieved evidence.
          Evaluate each response against its supporting sources.
        </div>
      </div>
    </div>
  );
}
