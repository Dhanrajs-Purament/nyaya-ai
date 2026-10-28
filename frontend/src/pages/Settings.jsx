import React, { useState } from 'react';
import { Save, RefreshCw, CheckCircle2, AlertTriangle } from 'lucide-react';

export default function Settings({ health, onRefreshHealth, isRefreshing }) {
  const [topK, setTopK] = useState(5);
  const [chunkSize, setChunkSize] = useState(800);
  const [chunkOverlap, setChunkOverlap] = useState(150);
  const [savedNotification, setSavedNotification] = useState(false);

  const handleSave = () => {
    localStorage.setItem(
      'nyayabench_settings',
      JSON.stringify({ topK, chunkSize, chunkOverlap })
    );
    setSavedNotification(true);
    setTimeout(() => setSavedNotification(false), 2500);
  };

  return (
    <div className="space-y-8 w-full py-2">
      {/* Header bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-white/10">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight font-sans">
              System Settings & Architecture Rules
            </h1>
            <span className="text-[10px] font-mono px-3 py-1 rounded-full bg-purple-500/10 border border-purple-500/30 text-purple-400 font-bold">
              Local Inference
            </span>
          </div>
          <p className="text-xs sm:text-sm text-slate-400 mt-2 max-w-3xl leading-relaxed">
            Configure RAG pipeline retrieval hyperparameters and verify local Ollama and ChromaDB connections.
          </p>
        </div>

        <button
          onClick={onRefreshHealth}
          disabled={isRefreshing}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#101626] border border-white/10 text-xs font-semibold text-slate-300 hover:text-white transition-all cursor-pointer shadow-sm self-start sm:self-center"
        >
          <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-purple-400' : ''}`} />
          <span>Test Connections</span>
        </button>
      </div>

      {/* Services Connectivity Panel */}
      <div className="rounded-3xl border border-white/10 bg-[#101626]/80 p-8 shadow-2xl space-y-6">
        <h2 className="text-sm font-bold text-white uppercase font-mono tracking-wider">
          Runtime Connection Status
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-xs sm:text-sm font-mono">
          {/* Ollama Card */}
          <div className="p-6 rounded-2xl border border-white/5 bg-[#080B14]/80 flex flex-col justify-between shadow-md space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-slate-200 font-bold text-sm">Ollama Inference Runtime</span>
              {health?.services?.ollama?.reachable ? (
                <span className="text-emerald-400 flex items-center gap-1.5 text-xs font-semibold">
                  <CheckCircle2 className="w-4 h-4" /> Connected
                </span>
              ) : (
                <span className="text-rose-400 flex items-center gap-1.5 text-xs font-semibold">
                  <AlertTriangle className="w-4 h-4" /> Unreachable
                </span>
              )}
            </div>
            <div className="text-slate-400 text-xs space-y-2 pt-2 border-t border-white/5">
              <div>URL: <span className="text-purple-300">http://localhost:11434</span></div>
              <div>Embedding Model: <span className="text-amber-300">nomic-embed-text</span></div>
              <div>Installed LLMs: <span className="text-slate-200">{health?.models?.map(m => m.id).join(', ') || 'gemma3:1b, gemma3:4b, qwen3:4b'}</span></div>
            </div>
          </div>

          {/* ChromaDB Card */}
          <div className="p-6 rounded-2xl border border-white/5 bg-[#080B14]/80 flex flex-col justify-between shadow-md space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-slate-200 font-bold text-sm">ChromaDB Vector Store</span>
              {health?.services?.chroma?.reachable ? (
                <span className="text-emerald-400 flex items-center gap-1.5 text-xs font-semibold">
                  <CheckCircle2 className="w-4 h-4" /> Connected
                </span>
              ) : (
                <span className="text-rose-400 flex items-center gap-1.5 text-xs font-semibold">
                  <AlertTriangle className="w-4 h-4" /> Unreachable
                </span>
              )}
            </div>
            <div className="text-slate-400 text-xs space-y-2 pt-2 border-t border-white/5">
              <div>Host: <span className="text-purple-300">localhost:8000</span></div>
              <div>Collection: <span className="text-blue-300">nyayabench_legal_corpus</span></div>
              <div>Storage: <span className="text-slate-200">Local Persistent SQLite / Parquet</span></div>
            </div>
          </div>
        </div>
      </div>

      {/* RAG Hyperparameters */}
      <div className="rounded-3xl border border-white/10 bg-[#101626]/80 p-8 shadow-2xl space-y-6">
        <h2 className="text-sm font-bold text-white uppercase font-mono tracking-wider">
          RAG Pipeline Hyperparameters
        </h2>

        <div className="space-y-6 text-xs sm:text-sm">
          {/* Top K */}
          <div className="space-y-2">
            <div className="flex justify-between text-slate-200 font-semibold">
              <label>Retrieval Top-K Chunks: <span className="text-purple-400 font-mono text-base">{topK}</span></label>
              <span className="text-slate-500 font-mono text-xs">Default: 5</span>
            </div>
            <input
              type="range"
              min="1"
              max="10"
              value={topK}
              onChange={(e) => setTopK(parseInt(e.target.value, 10))}
              className="w-full accent-purple-500 cursor-pointer h-2 bg-white/10 rounded-lg"
            />
            <p className="text-xs text-slate-400">
              Controls how many document passages are retrieved and presented to the models in each query.
            </p>
          </div>

          {/* Chunk Size & Overlap */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 pt-2">
            <div className="space-y-2">
              <label className="block text-slate-200 font-semibold">
                Chunk Size (Characters): <span className="text-purple-400 font-mono">{chunkSize}</span>
              </label>
              <input
                type="number"
                value={chunkSize}
                onChange={(e) => setChunkSize(parseInt(e.target.value, 10))}
                className="w-full p-3 rounded-xl bg-[#080B14] border border-white/10 text-white font-mono text-sm focus:outline-none focus:border-purple-500"
              />
            </div>

            <div className="space-y-2">
              <label className="block text-slate-200 font-semibold">
                Chunk Overlap (Characters): <span className="text-purple-400 font-mono">{chunkOverlap}</span>
              </label>
              <input
                type="number"
                value={chunkOverlap}
                onChange={(e) => setChunkOverlap(parseInt(e.target.value, 10))}
                className="w-full p-3 rounded-xl bg-[#080B14] border border-white/10 text-white font-mono text-sm focus:outline-none focus:border-purple-500"
              />
            </div>
          </div>

          <div className="pt-4 border-t border-white/10 flex items-center justify-between">
            {savedNotification ? (
              <span className="text-emerald-400 font-mono text-xs sm:text-sm flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4" /> Parameters saved successfully!
              </span>
            ) : (
              <span />
            )}
            <button
              onClick={handleSave}
              className="px-6 py-3 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs sm:text-sm flex items-center gap-2 shadow-lg shadow-purple-900/40 transition-all cursor-pointer"
            >
              <Save className="w-4 h-4" />
              <span>Save Parameters</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
