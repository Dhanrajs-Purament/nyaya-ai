import React from 'react';
import {
  Scale,
  LayoutDashboard,
  MessageSquare,
  Database,
  GitCompare,
  BarChart3,
  Settings,
  Circle,
  RefreshCw,
  Cpu
} from 'lucide-react';

export default function Navbar({ activeTab, setActiveTab, health, onRefreshHealth, isRefreshing }) {
  const isOllamaUp = health?.services?.ollama?.reachable;
  const isChromaUp = health?.services?.chroma?.reachable;
  const readyModelsCount = health?.models?.filter((m) => m.installed)?.length || 0;

  const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'chat', label: 'Chat', icon: MessageSquare },
    { id: 'knowledge', label: 'Knowledge Base', icon: Database },
    { id: 'compare', label: 'Model Comparison', icon: GitCompare },
    { id: 'analytics', label: 'Analytics', icon: BarChart3 },
    { id: 'settings', label: 'Settings', icon: Settings }
  ];

  return (
    <header className="sticky top-0 z-40 w-full border-b border-white/10 bg-[#080B14]/90 backdrop-blur-2xl">
      <div className="w-full px-5 sm:px-8 lg:px-12 h-20 flex items-center justify-between">
        {/* Brand Logo & Tagline */}
        <div
          onClick={() => setActiveTab('dashboard')}
          className="flex items-center gap-4 cursor-pointer group flex-shrink-0"
        >
          <div className="w-11 h-11 rounded-2xl bg-gradient-to-tr from-amber-500 via-purple-600 to-indigo-600 p-[1.5px] shadow-lg shadow-purple-900/30 group-hover:shadow-amber-500/30 transition-all duration-300">
            <div className="w-full h-full bg-[#080B14] rounded-2xl flex items-center justify-center">
              <Scale className="w-6 h-6 text-amber-400 group-hover:scale-110 transition-transform duration-300" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2.5">
              <span className="text-xl font-extrabold tracking-tight text-white font-sans">
                Nyaya<span className="text-transparent bg-clip-text bg-gradient-to-r from-amber-400 via-purple-400 to-blue-400">Bench</span>
              </span>
              <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-purple-500/10 border border-purple-500/30 text-purple-300">
                AI 2.0
              </span>
            </div>
            <p className="text-xs text-slate-400 hidden md:block tracking-wide mt-0.5">
              One Knowledge Base. Multiple Minds. Evidence-Driven Answers.
            </p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="hidden md:flex items-center gap-2 bg-[#101626]/90 p-1.5 rounded-2xl border border-white/10 shadow-lg">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`flex items-center gap-2.5 px-4 py-2 rounded-xl text-xs font-semibold tracking-wide transition-all duration-200 cursor-pointer ${
                  isActive
                    ? 'bg-gradient-to-r from-purple-600/40 to-indigo-600/40 text-white border border-purple-500/50 shadow-md shadow-purple-500/20'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-white/5'
                }`}
              >
                <Icon className={`w-4 h-4 ${isActive ? 'text-purple-400' : 'text-slate-400'}`} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>

        {/* System & Model Status Indicators */}
        <div className="flex items-center gap-3 flex-shrink-0">
          {/* Ollama & Chroma Status */}
          <div className="hidden lg:flex items-center gap-4 px-4 py-2 rounded-xl bg-[#101626]/90 border border-white/10 text-xs shadow-md">
            <div className="flex items-center gap-2" title="Local Ollama Inference Engine">
              <Circle
                className={`w-2.5 h-2.5 fill-current ${
                  isOllamaUp ? 'text-emerald-400 fill-emerald-400' : 'text-rose-400 fill-rose-400'
                }`}
              />
              <span className="text-slate-300 font-mono text-xs">Ollama</span>
            </div>

            <div className="w-[1px] h-3.5 bg-white/10" />

            <div className="flex items-center gap-2" title="ChromaDB Persistent Vector Store">
              <Circle
                className={`w-2.5 h-2.5 fill-current ${
                  isChromaUp ? 'text-emerald-400 fill-emerald-400' : 'text-rose-400 fill-rose-400'
                }`}
              />
              <span className="text-slate-300 font-mono text-xs">ChromaDB</span>
            </div>

            <div className="w-[1px] h-3.5 bg-white/10" />

            <div className="flex items-center gap-2" title="Detected Models">
              <Cpu className="w-3.5 h-3.5 text-purple-400" />
              <span className="text-slate-300 font-mono text-xs">
                {readyModelsCount}/3 Models
              </span>
            </div>
          </div>

          {/* Refresh Connection Button */}
          <button
            onClick={onRefreshHealth}
            disabled={isRefreshing}
            title="Refresh System Health & Model Status"
            className="p-2.5 rounded-xl bg-[#101626] border border-white/10 text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-purple-400' : ''}`} />
          </button>
        </div>
      </div>

      {/* Mobile Navigation bar */}
      <div className="md:hidden flex items-center justify-around border-t border-white/10 bg-[#080B14] py-2 px-3">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`flex flex-col items-center py-1.5 px-3 rounded-lg text-[11px] ${
                isActive ? 'text-purple-400 font-bold bg-white/5' : 'text-slate-400'
              }`}
            >
              <Icon className="w-4 h-4 mb-1" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </div>
    </header>
  );
}
