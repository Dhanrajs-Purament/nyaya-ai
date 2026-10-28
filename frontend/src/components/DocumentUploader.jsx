import React, { useState, useRef } from 'react';
import { UploadCloud, CheckCircle2, AlertCircle, Loader2, Sparkles, FolderOpen } from 'lucide-react';
import { uploadDocuments } from '../services/api';

export default function DocumentUploader({ onUploadSuccess, chunkSize = 800, chunkOverlap = 150 }) {
  const [isDragging, setIsDragging] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [progressMsg, setProgressMsg] = useState('');
  const [statusNotification, setStatusNotification] = useState(null);
  const fileInputRef = useRef(null);

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = async (e) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      await processFiles(e.dataTransfer.files);
    }
  };

  const handleFileChange = async (e) => {
    if (e.target.files && e.target.files.length > 0) {
      await processFiles(e.target.files);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const processFiles = async (files) => {
    const validFiles = [];
    const errors = [];

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      const ext = file.name.split('.').pop().toLowerCase();
      if (ext !== 'pdf' && ext !== 'txt') {
        errors.push(`${file.name}: Only .pdf and .txt files are supported`);
        continue;
      }
      if (file.size > 30 * 1024 * 1024) {
        errors.push(`${file.name}: Exceeds 30MB limit`);
        continue;
      }
      validFiles.push(file);
    }

    if (errors.length > 0) {
      setStatusNotification({ type: 'error', message: errors.join(', ') });
      return;
    }

    if (validFiles.length === 0) return;

    setIsProcessing(true);
    setProgressMsg(`Extracting text and chunking with page metadata (${chunkSize} chars, ${chunkOverlap} overlap)...`);
    setStatusNotification(null);

    try {
      setTimeout(() => {
        setProgressMsg('Generating nomic-embed-text embeddings and writing to ChromaDB...');
      }, 1000);

      const result = await uploadDocuments(validFiles);

      setStatusNotification({
        type: 'success',
        message: `Successfully indexed ${result.totalChunksIngested || result.documents?.length || 'document'} chunks into ChromaDB!`
      });

      if (onUploadSuccess) onUploadSuccess(result);
    } catch (err) {
      setStatusNotification({
        type: 'error',
        message: err.message || 'Ingestion failed'
      });
    } finally {
      setIsProcessing(false);
      setProgressMsg('');
    }
  };

  return (
    <div className="w-full space-y-4">
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".pdf,.txt"
        onChange={handleFileChange}
        className="hidden"
      />

      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => !isProcessing && fileInputRef.current?.click()}
        className={`relative rounded-3xl border-2 border-dashed p-10 sm:p-14 transition-all duration-300 text-center cursor-pointer ${
          isDragging
            ? 'border-purple-500 bg-purple-950/20 scale-[1.01]'
            : 'border-white/10 bg-[#101626]/70 hover:border-purple-500/50 hover:bg-[#141D30]/80 shadow-2xl'
        } ${isProcessing ? 'pointer-events-none opacity-80' : ''}`}
      >
        <div className="flex flex-col items-center justify-center gap-4 sm:gap-5 max-w-xl mx-auto">
          <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-purple-600/20 to-indigo-600/20 border border-purple-500/30 flex items-center justify-center text-purple-400 shadow-xl shadow-purple-900/30">
            {isProcessing ? (
              <Loader2 className="w-8 h-8 animate-spin text-purple-400" />
            ) : (
              <UploadCloud className="w-8 h-8 text-purple-400" />
            )}
          </div>

          <div className="space-y-1.5">
            <h3 className="text-lg sm:text-xl font-bold text-slate-100 tracking-tight">
              {isProcessing ? 'Processing Documents...' : 'Upload Legal Documents & Evidence'}
            </h3>
            <p className="text-xs sm:text-sm text-slate-400 max-w-md mx-auto leading-relaxed">
              Drag & drop <span className="text-slate-200 font-semibold">PDF</span> or{' '}
              <span className="text-slate-200 font-semibold">TXT</span> files here, or click to browse.
              Page numbers are extracted and preserved for precise source citations.
            </p>
          </div>

          {/* Explicit Browse Files Button */}
          <div className="pt-1">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                fileInputRef.current?.click();
              }}
              disabled={isProcessing}
              className="px-6 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs shadow-lg shadow-purple-900/40 transition-all cursor-pointer inline-flex items-center gap-2"
            >
              <FolderOpen className="w-4 h-4" />
              <span>Browse Files from Computer</span>
            </button>
          </div>

          <div className="flex flex-wrap items-center justify-center gap-2.5 pt-2">
            <span className="text-xs font-mono px-3 py-1 rounded-lg bg-white/5 border border-white/10 text-slate-300">
              Embedding: nomic-embed-text
            </span>
            <span className="text-xs font-mono px-3 py-1 rounded-lg bg-white/5 border border-white/10 text-slate-300">
              Chunk Size: {chunkSize} chars
            </span>
            <span className="text-xs font-mono px-3 py-1 rounded-lg bg-white/5 border border-white/10 text-slate-300">
              Overlap: {chunkOverlap} chars
            </span>
            <span className="text-xs font-mono px-3 py-1 rounded-lg bg-white/5 border border-white/10 text-slate-300">
              Max Size: 30MB
            </span>
          </div>

          {isProcessing && (
            <div className="mt-4 flex items-center gap-2 text-xs font-mono text-purple-300 animate-pulse">
              <Sparkles className="w-4 h-4" />
              <span>{progressMsg}</span>
            </div>
          )}
        </div>
      </div>

      {/* Status Notifications */}
      {statusNotification && (
        <div
          className={`p-4 rounded-2xl border flex items-center justify-between text-xs sm:text-sm transition-all shadow-md ${
            statusNotification.type === 'success'
              ? 'bg-emerald-950/20 border-emerald-500/30 text-emerald-300'
              : 'bg-rose-950/20 border-rose-500/30 text-rose-300'
          }`}
        >
          <div className="flex items-center gap-2.5">
            {statusNotification.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 text-rose-400 flex-shrink-0" />
            )}
            <span>{statusNotification.message}</span>
          </div>
          <button
            onClick={() => setStatusNotification(null)}
            className="text-slate-400 hover:text-white text-xs px-2.5 py-1 cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}
    </div>
  );
}
