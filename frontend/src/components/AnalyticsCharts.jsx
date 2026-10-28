import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
  CartesianGrid
} from 'recharts';
import { BarChart3, Clock, Zap, BookCheck, Database } from 'lucide-react';

const MODEL_COLORS = {
  'gemma3:1b': '#F97316',
  'gemma3:4b': '#8B5CF6',
  'qwen3:4b': '#3B82F6'
};

export default function AnalyticsCharts({ analyticsData }) {
  if (!analyticsData) {
    return (
      <div className="rounded-3xl border border-white/10 bg-[#101626]/50 p-16 text-center shadow-xl">
        <BarChart3 className="w-12 h-12 text-slate-600 mx-auto mb-4" />
        <h3 className="text-base font-bold text-slate-200">No Analytics Recorded Yet</h3>
        <p className="text-xs sm:text-sm text-slate-500 mt-2 max-w-md mx-auto leading-relaxed">
          Ask questions in the Chat or run a Model Comparison to populate real, measured inference benchmarks.
        </p>
      </div>
    );
  }

  const usage = analyticsData.usage || {};
  const retrieval = analyticsData.retrieval || {};
  const perfList = analyticsData.modelPerformance || [];

  const totalQueries = usage.totalQuestionsAsked || 0;
  const totalComparisons = usage.totalComparisonRequests || 0;
  const totalChunks = usage.totalChunksIndexed || 0;
  const avgRetrievalMs = retrieval.avgRetrievalDurationMs || 0;

  // Transform performance list for charts
  const latencyChartData = perfList.map((item) => ({
    name: item.name || item.modelId,
    modelId: item.modelId,
    avgLatency: item.avgLatencySeconds || 0,
    requests: item.requests || 0,
    avgTokens: item.avgTokensGenerated || 0,
    citationRate: item.citationValidationRate || 0
  }));

  const hasActivity = totalQueries > 0 || perfList.some((p) => p.requests > 0);

  if (!hasActivity) {
    return (
      <div className="rounded-3xl border border-white/10 bg-[#101626]/50 p-16 text-center shadow-xl">
        <BarChart3 className="w-12 h-12 text-slate-600 mx-auto mb-4" />
        <h3 className="text-base font-bold text-slate-200">Awaiting Real Telemetry</h3>
        <p className="text-xs sm:text-sm text-slate-500 mt-2 max-w-md mx-auto leading-relaxed">
          Execute single-model queries or multi-model comparisons to record authentic Ollama and ChromaDB metrics.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8 w-full">
      {/* Top Stat Summary Cards with Generous Spacing */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="p-6 rounded-3xl border border-white/10 bg-[#101626]/90 flex flex-col justify-between shadow-xl">
          <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm font-semibold">
            <span>Total Inquiries</span>
            <BookCheck className="w-5 h-5 text-purple-400" />
          </div>
          <div className="text-3xl font-extrabold font-mono text-white mt-3">{totalQueries}</div>
          <span className="text-xs text-slate-500 font-mono mt-1.5">Queries executed</span>
        </div>

        <div className="p-6 rounded-3xl border border-white/10 bg-[#101626]/90 flex flex-col justify-between shadow-xl">
          <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm font-semibold">
            <span>Model Comparisons</span>
            <Zap className="w-5 h-5 text-amber-400" />
          </div>
          <div className="text-3xl font-extrabold font-mono text-white mt-3">{totalComparisons}</div>
          <span className="text-xs text-slate-500 font-mono mt-1.5">Parallel benchmarks</span>
        </div>

        <div className="p-6 rounded-3xl border border-white/10 bg-[#101626]/90 flex flex-col justify-between shadow-xl">
          <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm font-semibold">
            <span>Indexed Chunks</span>
            <Database className="w-5 h-5 text-emerald-400" />
          </div>
          <div className="text-3xl font-extrabold font-mono text-white mt-3">{totalChunks}</div>
          <span className="text-xs text-slate-500 font-mono mt-1.5">ChromaDB passages</span>
        </div>

        <div className="p-6 rounded-3xl border border-white/10 bg-[#101626]/90 flex flex-col justify-between shadow-xl">
          <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm font-semibold">
            <span>Avg Retrieval Latency</span>
            <Clock className="w-5 h-5 text-blue-400" />
          </div>
          <div className="text-3xl font-extrabold font-mono text-white mt-3">{avgRetrievalMs}ms</div>
          <span className="text-xs text-slate-500 font-mono mt-1.5">Vector search speed</span>
        </div>
      </div>

      {/* Recharts Grid with Generous Height & Screen Utilization */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Latency by Model */}
        <div className="p-7 rounded-3xl border border-white/10 bg-[#101626]/90 shadow-2xl space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-white tracking-tight">Average Response Latency (Seconds)</h3>
              <p className="text-xs text-slate-400 mt-0.5">Lower latency indicates faster inference</p>
            </div>
            <Clock className="w-5 h-5 text-purple-400" />
          </div>

          <div className="h-80 w-full pt-2">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={latencyChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#ffffff10" vertical={false} />
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={12} tickLine={false} />
                <YAxis stroke="#94a3b8" fontSize={12} tickLine={false} unit="s" />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0d121f', borderColor: 'rgba(255,255,255,0.15)', borderRadius: '12px', fontSize: '12px' }}
                />
                <Bar dataKey="avgLatency" radius={[8, 8, 0, 0]}>
                  {latencyChartData.map((entry) => (
                    <Cell key={entry.modelId} fill={MODEL_COLORS[entry.modelId] || '#8B5CF6'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Tokens Generated */}
        <div className="p-7 rounded-3xl border border-white/10 bg-[#101626]/90 shadow-2xl space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-white tracking-tight">Average Tokens Generated per Answer</h3>
              <p className="text-xs text-slate-400 mt-0.5">Detailed explanations vs concise responses</p>
            </div>
            <Zap className="w-5 h-5 text-amber-400" />
          </div>

          <div className="h-80 w-full pt-2">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={latencyChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#ffffff10" vertical={false} />
                <XAxis dataKey="name" stroke="#94a3b8" fontSize={12} tickLine={false} />
                <YAxis stroke="#94a3b8" fontSize={12} tickLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0d121f', borderColor: 'rgba(255,255,255,0.15)', borderRadius: '12px', fontSize: '12px' }}
                />
                <Bar dataKey="avgTokens" radius={[8, 8, 0, 0]}>
                  {latencyChartData.map((entry) => (
                    <Cell key={entry.modelId} fill={MODEL_COLORS[entry.modelId] || '#06B6D4'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Model Performance Breakdown Table */}
      <div className="rounded-3xl border border-white/10 bg-[#101626]/90 overflow-hidden shadow-2xl">
        <div className="p-6 border-b border-white/10 bg-[#141D30]/60">
          <h3 className="text-base font-bold text-white tracking-tight">Telemetry Breakdown Table</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm">
            <thead className="bg-[#101626] border-b border-white/5 text-slate-400 font-mono text-xs uppercase tracking-wider">
              <tr>
                <th className="py-4 px-6">Model</th>
                <th className="py-4 px-4">Total Requests</th>
                <th className="py-4 px-4">Avg Latency</th>
                <th className="py-4 px-4">Avg Tokens</th>
                <th className="py-4 px-4">Success Rate</th>
                <th className="py-4 px-4">Citation Support</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5 text-slate-300 font-mono">
              {perfList.map((m) => (
                <tr key={m.modelId} className="hover:bg-white/[0.03] transition-colors">
                  <td className="py-4 px-6 font-bold text-white flex items-center gap-3">
                    <div className="w-3 h-3 rounded-full shadow-sm" style={{ backgroundColor: MODEL_COLORS[m.modelId] }} />
                    <span className="text-sm">{m.name}</span>
                  </td>
                  <td className="py-4 px-4">{m.requests}</td>
                  <td className="py-4 px-4 text-amber-400 font-semibold">{m.avgLatencySeconds}s</td>
                  <td className="py-4 px-4 text-purple-400 font-semibold">{m.avgTokensGenerated}</td>
                  <td className="py-4 px-4 text-emerald-400 font-semibold">{m.successRatePercentage}%</td>
                  <td className="py-4 px-4 text-blue-400 font-semibold">{m.citationValidationRate}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
