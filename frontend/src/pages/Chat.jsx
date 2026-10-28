import React, { useState, useRef, useEffect } from 'react';
import {
  Send,
  Sparkles,
  GitCompare,
  Trash2,
  Copy,
  Check,
  Clock,
  Zap,
  StopCircle,
  FileText
} from 'lucide-react';
import ModelSelector from '../components/ModelSelector';
import CitationCard from '../components/CitationCard';
import { streamSingleModel, querySingleModel } from '../services/api';

const PRESET_SUGGESTIONS = [
  'What are the key findings and conclusions in the uploaded document?',
  'What did Justice K.S. Puttaswamy hold regarding Article 21 and Privacy?',
  'Explain the Basic Structure Doctrine and Article 368 limitations in Kesavananda Bharati.',
  'How does the Bharatiya Nyaya Sanhita (BNS) treat Murder and Theft compared to IPC?'
];

export default function Chat({
  modelsStatus = [],
  selectedModel = 'gemma3:4b',
  setSelectedModel,
  onCompareQuestion,
  onRefreshHealth
}) {
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      role: 'assistant',
      content:
        'Welcome to NyayaBench AI. I am your document-grounded legal intelligence assistant.\n\nAsk any question about your uploaded documents or legal corpus. Answers are strictly grounded in retrieved evidence with verified page-level citations.',
      model: selectedModel,
      citations: []
    }
  ]);
  const [input, setInput] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [useStreaming, setUseStreaming] = useState(true);
  const [copiedId, setCopiedId] = useState(null);
  const [measuredMetrics, setMeasuredMetrics] = useState({});

  const abortControllerRef = useRef(null);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isGenerating]);

  const handleCopy = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleStop = () => {
    if (abortControllerRef.current) {
      try {
        abortControllerRef.current.abort();
      } catch {
        // Ignore synchronous abort error
      }
      setIsGenerating(false);
    }
  };

  const handleClear = () => {
    setMessages([
      {
        id: 'welcome',
        role: 'assistant',
        content: 'Conversation cleared. Ready for your next document inquiry.',
        model: selectedModel,
        citations: []
      }
    ]);
  };

  const handleSend = async (questionText = input) => {
    const q = questionText.trim();
    if (!q || isGenerating) return;

    setInput('');
    const userMsgId = 'user-' + Date.now();
    const assistantMsgId = 'assistant-' + Date.now();

    const userMessage = { id: userMsgId, role: 'user', content: q };
    const initialAssistantMessage = {
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      model: selectedModel,
      citations: [],
      isStreaming: true,
      metrics: null
    };

    setMessages((prev) => [...prev, userMessage, initialAssistantMessage]);
    setIsGenerating(true);

    abortControllerRef.current = new AbortController();

    if (useStreaming) {
      let accumulatedText = '';
      let citationsList = [];

      try {
        const result = await streamSingleModel(
          q,
          selectedModel,
          { topK: 5 },
          (token) => {
            accumulatedText += token;
            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === assistantMsgId ? { ...msg, content: accumulatedText } : msg
              )
            );
          },
          (citations) => {
            citationsList = citations || [];
            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === assistantMsgId ? { ...msg, citations: citationsList } : msg
              )
            );
          },
          abortControllerRef.current.signal
        );

        const wasAborted = abortControllerRef.current?.signal?.aborted || result?.aborted;

        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? {
                  ...msg,
                  content: wasAborted
                    ? (accumulatedText ? `${accumulatedText} [Generation stopped]` : '[Generation stopped]')
                    : (accumulatedText || 'No response generated from the selected model.'),
                  citations: citationsList.length > 0 ? citationsList : msg.citations,
                  metrics: result?.metrics || msg.metrics,
                  isStreaming: false
                }
              : msg
          )
        );
      } catch (err) {
        const wasAborted =
          abortControllerRef.current?.signal?.aborted ||
          err.name === 'AbortError' ||
          (err.message && err.message.toLowerCase().includes('abort'));

        if (wasAborted) {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMsgId
                ? {
                    ...msg,
                    isStreaming: false,
                    content: accumulatedText
                      ? `${accumulatedText} [Generation stopped]`
                      : '[Generation stopped]'
                  }
                : msg
            )
          );
        } else {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMsgId
                ? {
                    ...msg,
                    isStreaming: false,
                    content: `Generation Error: ${err.message}. Please verify local Ollama is active.`
                  }
                : msg
            )
          );
        }
      } finally {
        setIsGenerating(false);
      }
    } else {
      // Non-streaming fallback
      try {
        const result = await querySingleModel(q, selectedModel, 5);
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? {
                  ...msg,
                  content: result.answer,
                  citations: result.citations?.retrievedChunks || [],
                  metrics: result.metrics,
                  isStreaming: false
                }
              : msg
          )
        );
        if (result.metrics) {
          setMeasuredMetrics((prev) => ({
            ...prev,
            [selectedModel]: result.metrics
          }));
        }
      } catch (err) {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? {
                  ...msg,
                  content: `Error: ${err.message}`,
                  isStreaming: false
                }
              : msg
          )
        );
      } finally {
        setIsGenerating(false);
      }
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-8.5rem)] w-full space-y-5">
      {/* Top Model Selector Container */}
      <div className="flex-shrink-0 bg-[#080B14] pb-2">
        <ModelSelector
          selectedModel={selectedModel}
          onSelectModel={setSelectedModel}
          modelsStatus={modelsStatus}
          measuredMetrics={measuredMetrics}
          onRefresh={onRefreshHealth}
        />
      </div>

      {/* Suggested Quick Question Pills with comfortable spacing */}
      <div className="flex-shrink-0 flex items-center gap-2.5 overflow-x-auto py-1.5 text-xs scrollbar-thin scrollbar-thumb-white/10 hover:scrollbar-thumb-white/20">
        <span className="text-slate-400 font-mono text-xs font-semibold flex-shrink-0">
          Suggested:
        </span>
        {PRESET_SUGGESTIONS.map((s, idx) => (
          <button
            key={idx}
            onClick={() => handleSend(s)}
            disabled={isGenerating}
            className="flex-shrink-0 px-3.5 py-1.5 rounded-xl bg-[#101626] hover:bg-[#141D30] border border-white/10 text-slate-300 hover:text-white transition-all duration-200 disabled:opacity-50 text-xs shadow-sm cursor-pointer whitespace-nowrap"
          >
            {s}
          </button>
        ))}
      </div>

      {/* Chat Messages Scroll Window - Expands to Fill Available Space */}
      <div className="flex-1 overflow-y-auto rounded-3xl border border-white/10 bg-[#101626]/60 p-6 sm:p-8 space-y-6 shadow-inner">
        {messages.map((msg) => {
          const isUser = msg.role === 'user';
          const isCopied = copiedId === msg.id;

          return (
            <div
              key={msg.id}
              className={`flex flex-col ${isUser ? 'items-end' : 'items-start'} space-y-3`}
            >
              <div
                className={`max-w-[92%] sm:max-w-[82%] rounded-3xl p-5 text-sm leading-relaxed shadow-lg ${
                  isUser
                    ? 'bg-gradient-to-r from-purple-600 to-indigo-600 text-white rounded-br-none shadow-purple-900/30'
                    : 'bg-[#141D30] border border-white/10 text-slate-100 rounded-bl-none'
                }`}
              >
                {!isUser && msg.model && (
                  <div className="flex items-center justify-between pb-3 mb-3 border-b border-white/10 text-xs font-mono">
                    <span className="text-purple-400 font-bold uppercase tracking-wider">{msg.model}</span>
                    <button
                      onClick={() => handleCopy(msg.content, msg.id)}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                      title="Copy response"
                    >
                      {isCopied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                )}

                <div className="whitespace-pre-wrap font-sans select-text space-y-2">
                  {msg.content || (msg.isStreaming ? 'Synthesizing grounded analysis from documents...' : 'No response text available.')}
                  {msg.isStreaming && (
                    <span className="inline-block w-2.5 h-4 bg-purple-400 animate-pulse ml-1.5 translate-y-0.5 rounded-sm" />
                  )}
                </div>

                {/* Metrics bar for assistant messages */}
                {msg.metrics?.latencySeconds && (
                  <div className="mt-4 pt-3 border-t border-white/5 flex items-center gap-4 text-xs font-mono text-slate-400">
                    <span className="flex items-center gap-1.5">
                      <Clock className="w-3.5 h-3.5 text-amber-400" />
                      {msg.metrics.latencySeconds}s
                    </span>
                    {msg.metrics.tokensPerSecond > 0 && (
                      <span className="flex items-center gap-1.5">
                        <Zap className="w-3.5 h-3.5 text-purple-400" />
                        {msg.metrics.tokensPerSecond} t/s
                      </span>
                    )}
                  </div>
                )}
              </div>

              {/* Citations Drawer for Assistant Response with generous spacing */}
              {!isUser && msg.citations && msg.citations.length > 0 && (
                <div className="w-full max-w-[92%] sm:max-w-[82%] space-y-2.5 pt-1">
                  <div className="flex items-center gap-2 text-xs font-mono text-slate-400 px-1">
                    <FileText className="w-4 h-4 text-purple-400" />
                    <span>Supporting Source Citations ({msg.citations.length}):</span>
                  </div>
                  <div className="space-y-2.5">
                    {msg.citations.map((c, idx) => (
                      <CitationCard key={idx} citation={c} index={idx} />
                    ))}
                  </div>
                </div>
              )}
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {/* Input Box & Action Controls */}
      <div className="flex-shrink-0 bg-[#080B14] pt-2">
        <div className="relative rounded-3xl border border-white/10 bg-[#101626] p-4 focus-within:border-purple-500/80 shadow-2xl transition-all space-y-3">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={2}
            placeholder={`Ask a question with ${selectedModel} (e.g., "What are the key holdings and citations in this document?")...`}
            className="w-full bg-transparent px-2 py-1 text-sm text-white placeholder-slate-500 resize-none focus:outline-none min-h-[75px] leading-relaxed"
          />

          <div className="flex items-center justify-between pt-3 border-t border-white/10 px-2">
            <div className="flex items-center gap-4 text-xs">
              <button
                type="button"
                onClick={() => setUseStreaming(!useStreaming)}
                className="flex items-center gap-2 text-xs font-mono text-slate-300 hover:text-white cursor-pointer"
              >
                <div className={`w-2.5 h-2.5 rounded-full ${useStreaming ? 'bg-purple-400 shadow-sm shadow-purple-400/50' : 'bg-slate-600'}`} />
                <span>Stream SSE</span>
              </button>

              <button
                type="button"
                onClick={handleClear}
                className="text-slate-400 hover:text-slate-200 text-xs flex items-center gap-1.5 cursor-pointer transition-colors"
                title="Clear conversation"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Clear</span>
              </button>
            </div>

            <div className="flex items-center gap-3">
              {/* Compare All Models Button */}
              <button
                type="button"
                onClick={() => onCompareQuestion && onCompareQuestion(input.trim())}
                disabled={!input.trim() || isGenerating}
                className="px-4 py-2.5 rounded-xl bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 text-xs font-semibold flex items-center gap-2 transition-all cursor-pointer disabled:opacity-40"
              >
                <GitCompare className="w-4 h-4" />
                <span>Compare All Models</span>
              </button>

              {isGenerating ? (
                <button
                  type="button"
                  onClick={handleStop}
                  className="px-5 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-rose-900/40 transition-all cursor-pointer"
                >
                  <StopCircle className="w-4 h-4" />
                  <span>Stop</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => handleSend()}
                  disabled={!input.trim()}
                  className="px-6 py-2.5 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white text-xs font-bold flex items-center gap-2 shadow-lg shadow-purple-900/40 transition-all disabled:opacity-40 cursor-pointer"
                >
                  <Send className="w-4 h-4" />
                  <span>Send</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
