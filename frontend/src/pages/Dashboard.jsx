import React from 'react';
import {
  Scale,
  MessageSquare,
  GitCompare,
  Database,
  ArrowRight,
  Cpu,
  Layers,
  Sparkles
} from 'lucide-react';
import { MODEL_CONFIGS } from '../components/ModelSelector';

export default function Dashboard({
  setActiveTab,
  health,
  docStats = { totalChunks: 0, documents: [] },
  onNavigateToChatWithModel
}) {
  return (
    <div className="space-y-12 sm:space-y-16 w-full py-4">
      {/* Hero Section with Generous Padding & Full Screen Width */}
      <div className="relative rounded-3xl border border-white/10 bg-gradient-to-b from-[#101626]/95 via-[#0d121f]/95 to-[#080B14] p-8 sm:p-14 lg:p-16 overflow-hidden shadow-2xl">
        {/* Ambient Gradient Glows */}
        <div className="absolute top-0 right-1/4 w-[500px] h-[500px] bg-purple-600/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-1/4 w-[500px] h-[500px] bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 max-w-4xl space-y-6">
          <div className="inline-flex items-center gap-2.5 px-4 py-1.5 rounded-full bg-purple-500/10 border border-purple-500/30 text-purple-300 text-xs font-mono">
            <Sparkles className="w-4 h-4 text-amber-400" />
            <span>Local Multi-LLM RAG Intelligence Platform</span>
          </div>

          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold text-white tracking-tight leading-tight font-sans">
            Your Documents. <br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-400 via-purple-400 to-blue-400">
              Three AI Minds.
            </span>{' '}
            One Shared Truth.
          </h1>

          <p className="text-sm sm:text-base lg:text-lg text-slate-300 leading-relaxed max-w-3xl">
            Explore, question, compare, and evaluate multiple local AI models using the exact same document evidence.
            Zero cloud dependencies, verified page-level legal citations, and single-pass retrieval architecture.
          </p>

          {/* Action CTAs */}
          <div className="pt-4 flex flex-wrap items-center gap-4 sm:gap-5">
            <button
              onClick={() => setActiveTab('chat')}
              className="px-7 py-3.5 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-bold text-sm flex items-center gap-2.5 shadow-xl shadow-purple-900/40 hover:shadow-purple-700/50 transition-all cursor-pointer"
            >
              <MessageSquare className="w-4 h-4" />
              <span>Start Document Chat</span>
              <ArrowRight className="w-4 h-4" />
            </button>

            <button
              onClick={() => setActiveTab('compare')}
              className="px-7 py-3.5 rounded-xl bg-[#141D30] hover:bg-[#1a253d] border border-white/10 hover:border-white/25 text-white font-bold text-sm flex items-center gap-2.5 transition-all cursor-pointer shadow-md"
            >
              <GitCompare className="w-4 h-4 text-amber-400" />
              <span>Compare All 3 Models</span>
            </button>

            <button
              onClick={() => setActiveTab('knowledge')}
              className="px-6 py-3.5 rounded-xl bg-transparent hover:bg-white/5 text-slate-300 hover:text-white text-sm font-semibold flex items-center gap-2 transition-all cursor-pointer"
            >
              <Database className="w-4 h-4 text-emerald-400" />
              <span>Manage Documents ({docStats.documents?.length || 0})</span>
            </button>
          </div>
        </div>
      </div>

      {/* Model Showcase Cards */}
      <div className="space-y-5">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-white tracking-tight">The Three AI Minds</h2>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Independent reasoning models powered by your local Ollama runtime over identical ChromaDB context
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {MODEL_CONFIGS.map((model) => {
            const Icon = model.icon;
            return (
              <div
                key={model.id}
                onClick={() => onNavigateToChatWithModel && onNavigateToChatWithModel(model.id)}
                className="rounded-3xl border border-white/10 bg-[#101626]/80 hover:bg-[#141D30] p-7 flex flex-col justify-between transition-all duration-300 hover:border-purple-500/40 cursor-pointer group shadow-xl"
              >
                <div>
                  <div className="flex items-center justify-between mb-5">
                    <div
                      className="w-12 h-12 rounded-2xl flex items-center justify-center transition-transform group-hover:scale-110 shadow-md"
                      style={{ backgroundColor: `${model.accentColor}20` }}
                    >
                      <Icon className="w-6 h-6" style={{ color: model.accentColor }} />
                    </div>
                    <span className={`text-[11px] font-mono font-semibold px-2.5 py-0.5 rounded-full border ${model.badgeBg}`}>
                      {model.tag}
                    </span>
                  </div>

                  <h3 className="text-lg font-bold text-white tracking-tight">{model.name}</h3>
                  <p className="text-xs sm:text-sm text-slate-400 mt-2.5 leading-relaxed">
                    {model.description}
                  </p>
                </div>

                <div className="mt-8 pt-4 border-t border-white/5 flex items-center justify-between text-xs font-mono">
                  <span className="text-slate-400">Architecture: {model.size}</span>
                  <span
                    className="flex items-center gap-1.5 font-bold group-hover:translate-x-1 transition-transform"
                    style={{ color: model.accentColor }}
                  >
                    Select Model →
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Shared RAG Architecture Visualizer */}
      <div className="rounded-3xl border border-white/10 bg-[#101626]/60 p-8 sm:p-12 shadow-2xl space-y-8">
        <div className="text-center max-w-3xl mx-auto space-y-2">
          <span className="text-xs font-mono font-bold uppercase tracking-wider text-purple-400 bg-purple-500/10 px-3 py-1 rounded-full border border-purple-500/20">
            Core Architectural Principle
          </span>
          <h2 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
            Single Shared Retrieval Pipeline
          </h2>
          <p className="text-xs sm:text-sm text-slate-400 mt-2 leading-relaxed">
            Switching models never re-embeds documents or reconstructs vector indexes.
            The retrieval pipeline runs exactly once and feeds an immutable context object to all three models.
          </p>
        </div>

        {/* Visual Architecture Flow Grid with generous spacing */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 text-center">
          {/* Step 1 */}
          <div className="p-6 rounded-2xl border border-white/5 bg-[#080B14]/80 flex flex-col items-center justify-between space-y-3 shadow-md">
            <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400">
              <Database className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-200">1. Document Ingestion</h4>
              <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                PDF/TXT chunking (800 chars, 150 overlap) with page boundary preservation.
              </p>
            </div>
          </div>

          {/* Step 2 */}
          <div className="p-6 rounded-2xl border border-white/5 bg-[#080B14]/80 flex flex-col items-center justify-between space-y-3 shadow-md">
            <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
              <Layers className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-200">2. Nomic Embeddings</h4>
              <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                nomic-embed-text generates 768-dim vectors stored persistently in ChromaDB.
              </p>
            </div>
          </div>

          {/* Step 3 */}
          <div className="p-6 rounded-2xl border border-white/5 bg-[#080B14]/80 flex flex-col items-center justify-between space-y-3 shadow-md">
            <div className="w-12 h-12 rounded-xl bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-purple-400">
              <Cpu className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-200">3. Shared Retrieval</h4>
              <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                Single query embedding & ChromaDB top-K search generates immutable context.
              </p>
            </div>
          </div>

          {/* Step 4 */}
          <div className="p-6 rounded-2xl border border-white/5 bg-[#080B14]/80 flex flex-col items-center justify-between space-y-3 shadow-md">
            <div className="w-12 h-12 rounded-xl bg-blue-500/10 border border-blue-500/30 flex items-center justify-center text-blue-400">
              <GitCompare className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-slate-200">4. Parallel Inference</h4>
              <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                Gemma 1B, Gemma 4B, and Qwen 3 4B process identical context via Promise.allSettled.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
