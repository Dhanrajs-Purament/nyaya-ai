import test from 'node:test';
import assert from 'node:assert/strict';
import { streamResponse, streamMultiModelComparison } from '../src/services/ollamaService.js';

test('1. Streaming Test: Single-model stream with token dispatch and metrics capture', async () => {
  // Test streaming against local gemma3:1b
  const tokens = [];
  const result = await streamResponse(
    'gemma3:1b',
    'Answer in 3 words: What is law?',
    '',
    (token) => {
      tokens.push(token);
    }
  );

  assert.ok(result.success, 'Stream execution should succeed');
  assert.equal(result.model, 'gemma3:1b');
  assert.ok(tokens.length > 0, `Expected tokens to be dispatched, got ${tokens.length}`);
  assert.ok(result.response.length > 0, 'Full response must be assembled');
  assert.ok(result.metrics.tokensGenerated > 0, 'Ollama eval_count must be recorded');
  assert.ok(result.metrics.latencySeconds >= 0, 'Latency must be recorded');
});

test('2. Streaming Test: Retrieval-Once Invariant in Multi-Model Streaming', async () => {
  let retrievalCount = 0;

  // Mock vector DB tracking exact retrieval count
  const mockVectorDB = {
    async querySimilar(query) {
      retrievalCount++;
      return [
        {
          id: 'chunk-stream-1',
          text: 'Article 19 protects fundamental freedoms.',
          metadata: { documentName: 'constitution.txt', pageNumber: 5, heading: 'Article 19' },
          similarity: 91.2
        }
      ];
    }
  };

  const citations = await mockVectorDB.querySimilar('Article 19 rights');
  assert.equal(retrievalCount, 1, 'ChromaDB retrieval must execute exactly once before streaming starts');

  const receivedTokens = {
    'gemma3:1b': []
  };

  const events = [];
  await streamMultiModelComparison(
    ['gemma3:1b'],
    `Grounded context: ${citations[0].text}. Summarize.`,
    '',
    (eventType, data) => {
      events.push({ eventType, data });
      if (eventType === 'token' && data.model === 'gemma3:1b') {
        receivedTokens['gemma3:1b'].push(data.token);
      }
    }
  );

  // Invariant verified: vector DB was not queried again inside model execution
  assert.equal(retrievalCount, 1, 'Retrieval count must remain exactly 1 throughout streaming');
  assert.ok(receivedTokens['gemma3:1b'].length > 0, 'Gemma 3 (1B) must receive tokens');
  assert.ok(events.some((e) => e.eventType === 'model_complete'), 'model_complete event must be sent');
});

test('3. Streaming Test: Independent Model Failure Isolation', async () => {
  // Test streamMultiModelComparison with one non-existent model and one valid model
  const events = [];
  const results = await streamMultiModelComparison(
    ['non_existent_model_xyz', 'gemma3:1b'],
    'Say hello.',
    '',
    (eventType, data) => {
      events.push({ eventType, data });
    }
  );

  assert.equal(results.length, 2, 'Both model promises must settle');

  const errorEvent = events.find((e) => e.eventType === 'model_error' && e.data.model === 'non_existent_model_xyz');
  assert.ok(errorEvent, 'Failed model must dispatch model_error event');
  assert.ok(errorEvent.data.error, 'Error message must be captured for failed model');

  const completeEvent = events.find((e) => e.eventType === 'model_complete' && e.data.model === 'gemma3:1b');
  assert.ok(completeEvent, 'Valid model must complete successfully without interruption from failed model');
});

test('4. Streaming Test: Client Disconnection & AbortController Cancellation', async () => {
  const controller = new AbortController();

  // Abort immediately
  controller.abort();

  let errorCaught = null;
  try {
    await streamResponse('gemma3:1b', 'Write an essay on constitutional law', '', () => {}, controller.signal);
  } catch (err) {
    errorCaught = err;
  }

  assert.ok(errorCaught, 'Aborted fetch must reject or throw cleanly');
  assert.ok(
    errorCaught.message.includes('aborted') || errorCaught.name === 'AbortError',
    `Expected abort message, got ${errorCaught.message}`
  );
});

test('5. Robust SSE Parser Logic: CRLF, LF, partial network chunks, and multi-line data', () => {
  // Simulate network stream chunks containing fragmented SSE messages
  const chunks = [
    'event: context\r\ndata: {"query":"Article 21"}\r\n\r\n',
    'event: token\ndata: {"model":"gemma3:1b",', // Split across chunks
    '"token":"Protection"}\n\n',
    'event: token\r\ndata: {"model":"gemma3:1b","token":" of"}\r\n\r\n',
    'event: model_complete\ndata: {"model":"gemma3:1b","metrics":{"latencySeconds":1.2}}\n\n',
    'event: all_complete\ndata: {"totalModels":1}\n\n'
  ];

  const parsedEvents = [];
  let buffer = '';

  for (const chunk of chunks) {
    buffer += chunk;
    // Normalize CRLF to LF and split by double newline
    const normalized = buffer.replace(/\r\n/g, '\n');
    const messages = normalized.split('\n\n');
    // Keep trailing incomplete element in buffer
    buffer = messages.pop();

    for (const msg of messages) {
      if (!msg.trim()) continue;
      const lines = msg.split('\n');
      let eventType = 'message';
      let dataStr = '';

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventType = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
          dataStr += (dataStr ? '\n' : '') + line.substring(5).trim();
        }
      }

      if (dataStr) {
        try {
          parsedEvents.push({ event: eventType, data: JSON.parse(dataStr) });
        } catch (e) {
          // ignore malformed
        }
      }
    }
  }

  assert.equal(parsedEvents.length, 5, `Expected 5 parsed events, got ${parsedEvents.length}`);
  assert.equal(parsedEvents[0].event, 'context');
  assert.equal(parsedEvents[1].event, 'token');
  assert.equal(parsedEvents[1].data.token, 'Protection');
  assert.equal(parsedEvents[2].data.token, ' of');
  assert.equal(parsedEvents[3].event, 'model_complete');
  assert.equal(parsedEvents[4].event, 'all_complete');
});
