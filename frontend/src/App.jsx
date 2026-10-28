import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import Chat from './pages/Chat';
import KnowledgeBase from './pages/KnowledgeBase';
import Comparison from './pages/Comparison';
import Analytics from './pages/Analytics';
import Settings from './pages/Settings';
import { getHealth, getDocuments, getModels } from './services/api';
import './App.css';

export default function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [health, setHealth] = useState(null);
  const [modelsStatus, setModelsStatus] = useState([]);
  const [docStats, setDocStats] = useState({ totalChunks: 0, documents: [] });
  const [selectedModel, setSelectedModel] = useState('gemma3:4b');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [comparisonPreloadQuestion, setComparisonPreloadQuestion] = useState('');

  // Initial load
  useEffect(() => {
    fetchHealthData();
    fetchDocumentsData();
  }, []);

  const fetchHealthData = async () => {
    setIsRefreshing(true);
    try {
      const [healthRes, modelsRes] = await Promise.allSettled([
        getHealth(),
        getModels()
      ]);

      if (healthRes.status === 'fulfilled') {
        setHealth(healthRes.value);
      }
      if (modelsRes.status === 'fulfilled') {
        setModelsStatus(modelsRes.value.models || []);
      }
    } catch (err) {
      console.error('Failed to fetch health/models:', err);
    } finally {
      setIsRefreshing(false);
    }
  };

  const fetchDocumentsData = async () => {
    try {
      const data = await getDocuments();
      const docs = data.documents || [];
      const totalChunks = data.totalChunks !== undefined
        ? data.totalChunks
        : docs.reduce((acc, d) => acc + (d.chunksCount || d.chunkCount || 1), 0);
      setDocStats({
        totalChunks,
        documents: docs
      });
    } catch (err) {
      console.error('Failed to fetch documents:', err);
    }
  };

  const handleNavigateToChatWithModel = (modelId) => {
    setSelectedModel(modelId);
    setActiveTab('chat');
  };

  const handleCompareQuestion = (question) => {
    setComparisonPreloadQuestion(question);
    setActiveTab('compare');
  };

  return (
    <div className="min-h-screen bg-[#080B14] text-slate-100 flex flex-col font-sans selection:bg-purple-500/30 selection:text-white">
      {/* Top Navbar */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        health={health}
        onRefreshHealth={fetchHealthData}
        isRefreshing={isRefreshing}
      />

      {/* Main Page Content - Expansive Full Screen Width with Comfortable Spacing */}
      <main className="flex-1 w-full px-5 sm:px-8 lg:px-12 py-6 flex flex-col">
        {activeTab === 'dashboard' && (
          <Dashboard
            setActiveTab={setActiveTab}
            health={health}
            docStats={docStats}
            onNavigateToChatWithModel={handleNavigateToChatWithModel}
          />
        )}

        {activeTab === 'chat' && (
          <Chat
            modelsStatus={modelsStatus}
            selectedModel={selectedModel}
            setSelectedModel={setSelectedModel}
            onCompareQuestion={handleCompareQuestion}
            onRefreshHealth={fetchHealthData}
          />
        )}

        {activeTab === 'knowledge' && (
          <KnowledgeBase
            documents={docStats.documents}
            totalChunks={docStats.totalChunks}
            onRefreshDocuments={fetchDocumentsData}
            isRefreshing={isRefreshing}
          />
        )}

        {activeTab === 'compare' && (
          <Comparison
            initialQuestion={comparisonPreloadQuestion}
          />
        )}

        {activeTab === 'analytics' && (
          <Analytics />
        )}

        {activeTab === 'settings' && (
          <Settings
            health={health}
            onRefreshHealth={fetchHealthData}
            isRefreshing={isRefreshing}
          />
        )}
      </main>

      {/* Minimal Footer */}
      <footer className="border-t border-white/5 py-4 text-center text-xs text-slate-500 font-mono">
        <div className="w-full px-5 sm:px-8 lg:px-12 flex flex-col sm:flex-row items-center justify-between gap-3">
          <span>NyayaBench AI • Production Multi-LLM RAG Platform</span>
          <span className="text-[11px] text-slate-500">
            One Knowledge Base. Multiple Minds. Evidence-Driven Answers.
          </span>
        </div>
      </footer>
    </div>
  );
}
