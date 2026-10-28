import React, { useState, useEffect } from 'react';
import { RefreshCw, Trash2 } from 'lucide-react';
import AnalyticsCharts from '../components/AnalyticsCharts';
import { getAnalytics, clearAnalytics } from '../services/api';

export default function Analytics() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getAnalytics();
      setData(res);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleClear = async () => {
    if (!window.confirm('Reset all analytics telemetry? This will clear historical benchmarks.')) {
      return;
    }
    try {
      await clearAnalytics();
      await fetchData();
    } catch (err) {
      alert(`Clear failed: ${err.message}`);
    }
  };

  return (
    <div className="space-y-8 w-full py-2">
      {/* Header bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-white/10">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight font-sans">
              Performance & Telemetry Analytics
            </h1>
            <span className="text-[10px] font-mono px-3 py-1 rounded-full bg-purple-500/10 border border-purple-500/30 text-purple-400 font-bold">
              Live Measured Metrics
            </span>
          </div>
          <p className="text-xs sm:text-sm text-slate-400 mt-2 max-w-3xl leading-relaxed">
            Real inference latency, token speeds, citation coverage, and retrieval execution times
            measured directly from local Ollama and ChromaDB interactions.
          </p>
        </div>

        <div className="flex items-center gap-3 self-start sm:self-center">
          <button
            onClick={fetchData}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#101626] border border-white/10 text-xs font-semibold text-slate-300 hover:text-white transition-all cursor-pointer shadow-sm"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-purple-400' : ''}`} />
            <span>Refresh</span>
          </button>

          <button
            onClick={handleClear}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-rose-950/20 border border-rose-500/30 text-xs font-semibold text-rose-300 hover:bg-rose-900/30 transition-all cursor-pointer shadow-sm"
            title="Reset metrics counter"
          >
            <Trash2 className="w-4 h-4" />
            <span>Reset Telemetry</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="rounded-3xl border border-white/10 bg-[#101626]/50 p-16 text-center text-xs sm:text-sm text-slate-400 font-mono animate-pulse shadow-xl">
          Loading performance telemetry...
        </div>
      ) : error ? (
        <div className="rounded-3xl border border-rose-500/20 bg-rose-950/20 p-8 text-center text-xs sm:text-sm text-rose-300 shadow-xl">
          Error retrieving analytics: {error}
        </div>
      ) : (
        <AnalyticsCharts analyticsData={data} />
      )}
    </div>
  );
}
