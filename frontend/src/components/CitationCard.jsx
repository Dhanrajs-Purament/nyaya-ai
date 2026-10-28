import React, { useState } from 'react';
import { FileText, ChevronDown, ChevronUp, CheckCircle, AlertCircle, Bookmark } from 'lucide-react';

export default function CitationCard({ citation, index, onPreview }) {
  const [expanded, setExpanded] = useState(false);

  // citation properties from retrievalService / citationService
  const docName = citation.filename || citation.documentName || 'Document';
  const pageNumber = citation.pageNumber ?? citation.page_number;
  const chunkIndex = citation.chunkIndex ?? citation.chunk_index;
  const tag = citation.tag || `S${index + 1}`;
  const text = citation.text || citation.snippet || '';
  const similarity = citation.similarity ?? (citation.distance !== undefined ? Math.round((1 - citation.distance) * 100) : null);
  const isValid = citation.isValid ?? true;

  return (
    <div className="rounded-xl border border-white/10 bg-[#101626]/80 hover:bg-[#141D30] transition-all duration-200 overflow-hidden text-xs">
      {/* Header bar */}
      <div
        onClick={() => setExpanded(!expanded)}
        className="px-3 py-2.5 flex items-center justify-between cursor-pointer select-none"
      >
        <div className="flex items-center gap-2 overflow-hidden">
          {/* Source Tag Badge */}
          <span className="font-mono font-bold px-2 py-0.5 rounded bg-purple-500/15 border border-purple-500/30 text-purple-300 text-[11px] flex-shrink-0">
            [{tag}]
          </span>

          <FileText className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />

          {/* Filename & Page */}
          <div className="flex items-center gap-1.5 truncate">
            <span className="font-medium text-slate-200 truncate">{docName}</span>
            <span className="text-slate-500">•</span>
            <span className="text-amber-400/90 font-mono text-[11px] flex-shrink-0">
              {pageNumber !== null && pageNumber !== undefined
                ? `Page ${pageNumber}`
                : `Chunk ${chunkIndex !== undefined ? chunkIndex + 1 : index + 1}`}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2 flex-shrink-0">
          {similarity !== null && (
            <span
              className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/20 text-emerald-400"
              title="Semantic Similarity"
            >
              {similarity}% match
            </span>
          )}

          {isValid ? (
            <span className="flex items-center gap-1 text-[10px] text-emerald-400 font-mono" title="Validated against ChromaDB retrieval">
              <CheckCircle className="w-3 h-3 text-emerald-400" />
              <span className="hidden sm:inline">Verified</span>
            </span>
          ) : (
            <span className="flex items-center gap-1 text-[10px] text-amber-400 font-mono" title="Unverified citation coverage">
              <AlertCircle className="w-3 h-3 text-amber-400" />
              <span className="hidden sm:inline">Unverified</span>
            </span>
          )}

          {expanded ? (
            <ChevronUp className="w-3.5 h-3.5 text-slate-400" />
          ) : (
            <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
          )}
        </div>
      </div>

      {/* Expanded context snippet */}
      {expanded && (
        <div className="px-3 pb-3 pt-1 border-t border-white/5 bg-[#080B14]/60">
          <p className="text-slate-300 text-[11.5px] leading-relaxed font-sans whitespace-pre-wrap select-text">
            {text || 'No text snippet available.'}
          </p>

          {onPreview && (
            <div className="mt-2.5 flex justify-end">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onPreview(citation);
                }}
                className="flex items-center gap-1 text-[11px] text-purple-400 hover:text-purple-300 font-medium transition-colors"
              >
                <Bookmark className="w-3 h-3" />
                <span>Open Document Context</span>
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
