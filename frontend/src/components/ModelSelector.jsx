import React from 'react';
import { Sparkles, Zap, BrainCircuit, RefreshCw, CheckCircle2, AlertTriangle, Clock } from 'lucide-react';

export const MODEL_CONFIGS = [
  {
    id: 'gemma3:1b',
    name: 'Gemma 3 (1B)',
    size: '1B',
    accentColor: '#F97316', // Orange
    borderActive: 'border-orange-500/80 ring-1 ring-orange-500/30',
    bgActive: 'bg-orange-950/20',
    glowActive: 'shadow-orange-500/20',
    badgeBg: 'bg-orange-500/10 text-orange-400 border-orange-500/30',
    icon: Zap,
    tag: 'Lightweight',
    description:
      'Lightweight and responsive. Designed for quick answers, simple document queries, and low-latency interactions. May provide less detailed reasoning on complex questions.'
  },
  {
    id: 'gemma3:4b',
    name: 'Gemma 3 (4B)',
    size: '4B',
    accentColor: '#8B5CF6', // Purple
    borderActive: 'border-purple-500/80 ring-1 ring-purple-500/30',
    bgActive: 'bg-purple-950/20',
    glowActive: 'shadow-purple-500/20',
    badgeBg: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
    icon: Sparkles,
    tag: 'Recommended Default',
    isDefault: true,
    description:
      'Balanced intelligence and response quality. Suitable for detailed document analysis, summarization, and multi-step questions. Recommended default model.'
  },
  {
    id: 'qwen3:4b',
    name: 'Qwen 3 (4B)',
    size: '4B',
    accentColor: '#3B82F6', // Blue
    borderActive: 'border-blue-500/80 ring-1 ring-blue-500/30',
    bgActive: 'bg-blue-950/20',
    glowActive: 'shadow-blue-500/20',
    badgeBg: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
    icon: BrainCircuit,
    tag: 'High-Reasoning',
    description:
      'Reasoning-oriented alternative for analytical questions and structured explanations. Compare its results against Gemma using identical evidence.'
  }
];

export default function ModelSelector({
  selectedModel,
  onSelectModel,
  modelsStatus = [],
  measuredMetrics = {},
  onRefresh,
  isRefreshing
}) {
  return (
    <div className="w-full space-y-4">
      {/* Header bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-1">
        <div className="flex items-center gap-3">
          <h2 className="text-sm font-bold tracking-wider text-slate-200 uppercase font-mono">
            Choose Your AI Model
          </h2>
          <span className="text-xs text-slate-400 hidden sm:inline">
            • Switching models preserves context without re-embedding
          </span>
        </div>

        {onRefresh && (
          <button
            onClick={onRefresh}
            disabled={isRefreshing}
            className="flex items-center gap-2 text-xs font-medium text-slate-400 hover:text-purple-300 transition-colors self-start sm:self-auto cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-purple-400' : ''}`} />
            <span>Refresh Availability</span>
          </button>
        )}
      </div>

      {/* Model Cards Grid with Generous Spacing */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5 sm:gap-4 lg:gap-5">
        {MODEL_CONFIGS.map((cfg) => {
          const Icon = cfg.icon;
          const isSelected = selectedModel === cfg.id;

          const modelInfo = modelsStatus.find((m) => m.id === cfg.id);
          const isAvailable = modelInfo ? (modelInfo.available ?? modelInfo.installed ?? true) : true;
          const metrics = measuredMetrics[cfg.id];

          return (
            <div
              key={cfg.id}
              onClick={() => isAvailable && onSelectModel(cfg.id)}
              className={`relative rounded-2xl p-4 sm:p-5 border transition-all duration-300 cursor-pointer text-left select-none flex flex-col justify-between ${
                !isAvailable
                  ? 'opacity-60 border-rose-500/30 bg-[#101626]/50 cursor-not-allowed'
                  : isSelected
                  ? `${cfg.borderActive} ${cfg.bgActive} shadow-xl ${cfg.glowActive}`
                  : 'border-white/10 bg-[#101626]/80 hover:border-white/20 hover:bg-[#141D30]'
              }`}
            >
              <div>
                {/* Header with Icon, Name, and Selection Pill */}
                <div className="flex items-start justify-between gap-2.5 mb-2.5">
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl flex-shrink-0 flex items-center justify-center shadow-md"
                      style={{ backgroundColor: `${cfg.accentColor}20` }}
                    >
                      <Icon className="w-4 h-4 sm:w-5 sm:h-5" style={{ color: cfg.accentColor }} />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <span className="text-xs sm:text-sm font-bold text-white tracking-tight truncate">{cfg.name}</span>
                        <span className="text-[10px] font-mono px-1.5 py-0.5 rounded-md bg-white/5 text-slate-300 border border-white/5">
                          {cfg.size}
                        </span>
                      </div>
                      <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded-full border mt-1 whitespace-nowrap ${cfg.badgeBg}`}>
                        {cfg.tag}
                      </span>
                    </div>
                  </div>

                  {/* Radio Indicator */}
                  <div className="flex-shrink-0">
                    {isAvailable ? (
                      <div
                        className={`w-5 h-5 rounded-full border flex items-center justify-center transition-all ${
                          isSelected ? 'border-transparent shadow-md' : 'border-white/20'
                        }`}
                        style={{
                          backgroundColor: isSelected ? cfg.accentColor : 'transparent'
                        }}
                      >
                        {isSelected && <div className="w-2 h-2 rounded-full bg-white" />}
                      </div>
                    ) : (
                      <span className="flex items-center text-[10px] text-rose-400 font-mono">
                        <AlertTriangle className="w-3.5 h-3.5 mr-1" /> Missing
                      </span>
                    )}
                  </div>
                </div>

                {/* Description Text */}
                <p className="text-xs text-slate-300/90 leading-relaxed mb-3 line-clamp-3 sm:line-clamp-4">
                  {cfg.description}
                </p>
              </div>

              {/* Footer Metrics */}
              <div className="pt-3 border-t border-white/5 flex items-center justify-between text-xs font-mono">
                {isAvailable ? (
                  <>
                    <div className="flex items-center gap-1.5 text-slate-400">
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                      <span>Ready</span>
                    </div>

                    {metrics?.latencySeconds ? (
                      <div className="flex items-center gap-1.5 text-slate-200" title="Actual measured response time">
                        <Clock className="w-3.5 h-3.5 text-amber-400" />
                        <span>{metrics.latencySeconds}s</span>
                        {metrics.tokensPerSecond > 0 && (
                          <span className="text-slate-400">({metrics.tokensPerSecond} t/s)</span>
                        )}
                      </div>
                    ) : (
                      <span className="text-slate-500">Ready for query</span>
                    )}
                  </>
                ) : (
                  <div className="text-rose-400 text-[11px] flex items-center gap-1">
                    <span>Model not found. Please pull using Ollama.</span>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
