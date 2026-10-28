import React, { useState } from 'react';
import {
  FileText,
  Trash2,
  Search,
  Layers,
  HardDrive,
  Eye,
  X,
  BookOpen,
  RotateCcw,
  CheckCircle2,
  AlertCircle,
  Loader2
} from 'lucide-react';
import { deleteDocument, getDocumentPreview, loadSampleDocuments, resetCorpus } from '../services/api';

export default function DocumentList({ documents = [], totalChunks = 0, onRefresh }) {
  const [searchQuery, setSearchQuery] = useState('');
  const [deletingId, setDeletingId] = useState(null);
  const [loadingAction, setLoadingAction] = useState(false);
  const [actionMessage, setActionMessage] = useState(null);
  const [previewDoc, setPreviewDoc] = useState(null);
  const [loadingPreview, setLoadingPreview] = useState(false);

  const filtered = documents.filter((doc) => {
    const q = searchQuery.toLowerCase();
    const name = (doc.name || doc.filename || '').toLowerCase();
    return name.includes(q);
  });

  const handleDelete = async (docId, docName) => {
    if (!window.confirm(`Delete "${docName}"? This will remove all chunks and embeddings from ChromaDB.`)) {
      return;
    }

    setDeletingId(docId);
    try {
      await deleteDocument(docId);
      setActionMessage({ type: 'success', text: `Deleted "${docName}" and cleaned up ChromaDB vectors.` });
      if (onRefresh) await onRefresh();
    } catch (err) {
      setActionMessage({ type: 'error', text: `Delete error: ${err.message}` });
    } finally {
      setDeletingId(null);
    }
  };

  const handlePreview = async (doc) => {
    setLoadingPreview(true);
    const targetId = doc.document_id || doc.id || doc.documentId || doc.filename;
    try {
      const data = await getDocumentPreview(targetId);
      setPreviewDoc({
        name: doc.name || doc.filename,
        pageCount: doc.numPages || doc.pageCount,
        chunkCount: doc.chunksCount || doc.chunkCount,
        ...data
      });
    } catch (err) {
      setPreviewDoc({
        name: doc.name || doc.filename,
        pageCount: doc.numPages || doc.pageCount,
        chunkCount: doc.chunksCount || doc.chunkCount,
        previewText: doc.previewSnippet || doc.sampleSnippet || 'Document preview is currently being indexed.'
      });
    } finally {
      setLoadingPreview(false);
    }
  };

  const handleLoadSample = async () => {
    setLoadingAction(true);
    setActionMessage(null);
    try {
      const res = await loadSampleDocuments();
      setActionMessage({ type: 'success', text: res.message || 'Sample legal corpus loaded into ChromaDB!' });
      if (onRefresh) await onRefresh();
    } catch (err) {
      setActionMessage({ type: 'error', text: `Load sample error: ${err.message}` });
    } finally {
      setLoadingAction(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm('Reset Knowledge Base? This will clear all documents and ChromaDB vectors.')) {
      return;
    }
    setLoadingAction(true);
    setActionMessage(null);
    try {
      await resetCorpus();
      setActionMessage({ type: 'success', text: 'Knowledge base and ChromaDB collection reset successfully.' });
      if (onRefresh) await onRefresh();
    } catch (err) {
      setActionMessage({ type: 'error', text: `Reset error: ${err.message}` });
    } finally {
      setLoadingAction(false);
    }
  };

  return (
    <div className="w-full space-y-5">
      {/* Search & Actions Bar with Sample Load and Reset */}
      <div className="flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-4">
        {/* Search Input */}
        <div className="relative w-full lg:w-96">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search indexed legal documents..."
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[#101626] border border-white/10 text-xs sm:text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500 transition-all shadow-sm"
          />
        </div>

        {/* Action Buttons & Counters */}
        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={handleLoadSample}
            disabled={loadingAction}
            className="px-4 py-2 rounded-xl bg-purple-500/15 hover:bg-purple-500/25 border border-purple-500/30 text-purple-300 text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer disabled:opacity-50 shadow-sm"
            title="Load bundled Indian legal statutes and judgments"
          >
            {loadingAction ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <BookOpen className="w-3.5 h-3.5" />}
            <span>Load Sample Legal Corpus</span>
          </button>

          <button
            onClick={handleReset}
            disabled={loadingAction}
            className="px-3.5 py-2 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 text-rose-300 text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer disabled:opacity-50"
            title="Wipe collection and reset ChromaDB"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset</span>
          </button>

          <span className="px-3 py-2 rounded-xl bg-[#101626] border border-white/10 font-mono text-xs text-slate-300">
            {documents.length} Docs
          </span>
          <span className="px-3 py-2 rounded-xl bg-[#101626] border border-white/10 font-mono text-xs text-purple-400 font-bold">
            {totalChunks} Chunks
          </span>
        </div>
      </div>

      {/* Action Notification Toast */}
      {actionMessage && (
        <div
          className={`p-3.5 rounded-2xl border flex items-center justify-between text-xs sm:text-sm transition-all shadow-md ${
            actionMessage.type === 'success'
              ? 'bg-emerald-950/20 border-emerald-500/30 text-emerald-300'
              : 'bg-rose-950/20 border-rose-500/30 text-rose-300'
          }`}
        >
          <div className="flex items-center gap-2.5">
            {actionMessage.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
            )}
            <span>{actionMessage.text}</span>
          </div>
          <button
            onClick={() => setActionMessage(null)}
            className="text-slate-400 hover:text-white text-xs px-2 py-0.5 cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}

      {/* Documents Table / Card List */}
      {filtered.length === 0 ? (
        <div className="rounded-3xl border border-white/10 bg-[#101626]/50 p-12 text-center shadow-lg space-y-3">
          <HardDrive className="w-10 h-10 text-slate-600 mx-auto" />
          <p className="text-base font-bold text-slate-300">No documents found in knowledge base</p>
          <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
            {searchQuery
              ? 'No documents matched your query. Try searching another term.'
              : 'Upload PDF or TXT files above, or click "Load Sample Legal Corpus" to populate landmark Indian judgments and statutory provisions.'}
          </p>
          {!searchQuery && (
            <div className="pt-2">
              <button
                onClick={handleLoadSample}
                disabled={loadingAction}
                className="px-5 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold transition-all shadow-lg shadow-purple-900/40 cursor-pointer inline-flex items-center gap-2"
              >
                <BookOpen className="w-4 h-4" />
                <span>Load Sample Legal Corpus</span>
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className="rounded-3xl border border-white/10 bg-[#101626]/80 overflow-hidden shadow-2xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs sm:text-sm">
              <thead className="bg-[#141D30]/80 border-b border-white/10 text-slate-400 font-mono text-xs uppercase tracking-wider">
                <tr>
                  <th className="py-4 px-6">Document</th>
                  <th className="py-4 px-4">Type</th>
                  <th className="py-4 px-4">Chunks</th>
                  <th className="py-4 px-4">Pages</th>
                  <th className="py-4 px-4">Indexed Date</th>
                  <th className="py-4 px-6 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5 text-slate-300">
                {filtered.map((doc, idx) => {
                  const docId = doc.document_id || doc.id || doc.documentId || `doc_${idx}`;
                  const docName = doc.name || doc.filename || 'Legal Document';
                  const isPdf =
                    doc.file_type === 'pdf' ||
                    doc.fileType === 'pdf' ||
                    docName.toLowerCase().endsWith('.pdf');
                  const chunkNum = doc.chunksCount ?? doc.chunkCount ?? doc.chunks?.length ?? 1;
                  const pageNum = doc.numPages ?? doc.pageCount ?? (isPdf ? 'Multiple' : '1');
                  const uploadDate =
                    doc.uploadedAt || doc.upload_timestamp
                      ? new Date(doc.uploadedAt || doc.upload_timestamp).toLocaleDateString()
                      : 'Recently';

                  return (
                    <tr key={String(docId)} className="hover:bg-white/[0.03] transition-colors">
                      {/* Document Name */}
                      <td className="py-4 px-6">
                        <div className="flex items-center gap-3">
                          <div
                            className={`p-2 rounded-xl shadow-sm ${
                              isPdf ? 'bg-rose-500/10 text-rose-400' : 'bg-blue-500/10 text-blue-400'
                            }`}
                          >
                            <FileText className="w-5 h-5" />
                          </div>
                          <div>
                            <span className="font-semibold text-slate-100 block truncate max-w-sm sm:max-w-md">
                              {docName}
                            </span>
                            <span className="text-[11px] font-mono text-slate-500">
                              {String(docId).slice(0, 20)}...
                            </span>
                          </div>
                        </div>
                      </td>

                      {/* File Type */}
                      <td className="py-4 px-4 font-mono">
                        <span
                          className={`px-2.5 py-1 rounded-md text-[11px] font-bold ${
                            isPdf
                              ? 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                              : 'bg-blue-500/15 text-blue-300 border border-blue-500/30'
                          }`}
                        >
                          {isPdf ? 'PDF' : 'TXT'}
                        </span>
                      </td>

                      {/* Chunks */}
                      <td className="py-4 px-4 font-mono text-slate-200">
                        <div className="flex items-center gap-2">
                          <Layers className="w-4 h-4 text-purple-400" />
                          <span className="font-semibold">{chunkNum}</span>
                        </div>
                      </td>

                      {/* Pages */}
                      <td className="py-4 px-4 font-mono text-slate-400">
                        {pageNum}
                      </td>

                      {/* Date */}
                      <td className="py-4 px-4 font-mono text-slate-400">
                        {uploadDate}
                      </td>

                      {/* Actions */}
                      <td className="py-4 px-6 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handlePreview(doc)}
                            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                            title="Inspect Chunks & Preview"
                          >
                            <Eye className="w-4 h-4" />
                          </button>

                          <button
                            onClick={() => handleDelete(docId, docName)}
                            disabled={deletingId === docId}
                            className="p-2 rounded-xl text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 transition-colors disabled:opacity-50 cursor-pointer"
                            title="Delete Document and ChromaDB Vectors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Preview Modal */}
      {previewDoc && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/85 backdrop-blur-md">
          <div className="bg-[#101626] border border-white/10 rounded-3xl max-w-4xl w-full p-8 shadow-2xl relative max-h-[85vh] flex flex-col space-y-4">
            <div className="flex items-center justify-between pb-4 border-b border-white/10">
              <div className="flex items-center gap-3">
                <FileText className="w-6 h-6 text-purple-400" />
                <h3 className="font-bold text-white text-base">{previewDoc.name}</h3>
              </div>
              <button
                onClick={() => setPreviewDoc(null)}
                className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-white/10 cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="py-2 overflow-y-auto flex-1 font-mono text-xs text-slate-300 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-xs p-4 rounded-xl bg-black/40 border border-white/5">
                <div>Chunks: <span className="text-purple-400 font-bold">{previewDoc.chunkCount || 'Indexed'}</span></div>
                <div>Pages: <span className="text-amber-400 font-bold">{previewDoc.pageCount || 'Preserved'}</span></div>
              </div>

              <div className="p-4 rounded-2xl bg-[#080B14] border border-white/5 text-xs leading-relaxed whitespace-pre-wrap font-sans text-slate-300 max-h-96 overflow-y-auto select-text">
                {previewDoc.previewText || previewDoc.content || previewDoc.sampleSnippet || 'Extracting verified document text snippet...'}
              </div>
            </div>

            <div className="pt-4 border-t border-white/10 flex justify-end">
              <button
                onClick={() => setPreviewDoc(null)}
                className="px-6 py-2.5 rounded-xl bg-white/10 hover:bg-white/15 text-white text-xs font-semibold cursor-pointer transition-colors"
              >
                Close Preview
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
