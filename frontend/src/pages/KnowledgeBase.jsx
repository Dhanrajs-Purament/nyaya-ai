import React, { useEffect } from 'react';
import { RefreshCw } from 'lucide-react';
import DocumentUploader from '../components/DocumentUploader';
import DocumentList from '../components/DocumentList';

export default function KnowledgeBase({
  documents = [],
  totalChunks = 0,
  onRefreshDocuments,
  isRefreshing
}) {
  // Always fetch latest documents on mount
  useEffect(() => {
    if (typeof onRefreshDocuments === 'function') {
      onRefreshDocuments();
    }
  }, []);

  return (
    <div className="space-y-10 w-full py-2">
      {/* Header bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-white/10">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight font-sans">
              Knowledge Base & Vector Store
            </h1>
            <span className="text-[10px] font-mono px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
              Persistent ChromaDB
            </span>
          </div>
          <p className="text-xs sm:text-sm text-slate-400 mt-2 max-w-3xl leading-relaxed">
            Upload Indian legal judgments, statutory provisions, or general documentation. Content is chunked with page-level
            tracking and embedded via local <span className="font-mono text-purple-300">nomic-embed-text</span>.
          </p>
        </div>

        <button
          onClick={onRefreshDocuments}
          disabled={isRefreshing}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#101626] border border-white/10 text-xs font-semibold text-slate-300 hover:text-white hover:bg-white/5 transition-all self-start sm:self-center cursor-pointer shadow-sm"
        >
          <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-purple-400' : ''}`} />
          <span>Refresh Knowledge Base</span>
        </button>
      </div>

      {/* Upload Area */}
      <div className="space-y-4">
        <h2 className="text-sm font-bold tracking-wider text-slate-200 uppercase font-mono">
          Upload Documents
        </h2>
        <DocumentUploader onUploadSuccess={onRefreshDocuments} />
      </div>

      {/* Document Catalog */}
      <div className="space-y-4 pt-4">
        <h2 className="text-sm font-bold tracking-wider text-slate-200 uppercase font-mono">
          Indexed Document Registry
        </h2>
        <DocumentList
          documents={documents}
          totalChunks={totalChunks}
          onRefresh={onRefreshDocuments}
        />
      </div>
    </div>
  );
}
