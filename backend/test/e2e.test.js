import test from 'node:test';
import assert from 'node:assert/strict';

test('E2E: Health endpoint returns reachable Ollama, ChromaDB and detected models', async () => {
  const res = await fetch('http://localhost:5000/api/health');
  assert.equal(res.status, 200);
  const data = await res.json();

  assert.equal(data.status, 'ok');
  assert.equal(data.services.ollama.reachable, true);
  assert.equal(data.services.chroma.reachable, true);
  assert.equal(data.services.ollama.hasEmbeddingModel, true);

  const models = data.benchmarkModels;
  assert.equal(models.length, 3);
  assert.ok(models.find((m) => m.id === 'gemma3:1b')?.installed);
  assert.ok(models.find((m) => m.id === 'gemma3:4b')?.installed);
  assert.ok(models.find((m) => m.id === 'qwen3:4b')?.installed);
});

test('E2E: Semantic Search against ChromaDB returns relevant legal citations with page numbers', async () => {
  const res = await fetch('http://localhost:5000/api/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      query: 'What did Puttaswamy hold on Article 21 and privacy?',
      topK: 2
    })
  });

  assert.equal(res.status, 200);
  const data = await res.json();
  assert.ok(data.results.length > 0, 'Expected at least 1 citation returned');

  const firstResult = data.results[0];
  assert.ok(firstResult.similarity > 50, `Expected high similarity, got ${firstResult.similarity}%`);
  assert.ok(firstResult.metadata.documentName, 'Document name must be present');
  assert.ok(firstResult.metadata.pageNumber !== undefined, 'Page number must be present');
  assert.ok(firstResult.text.length > 0, 'Snippet text must be present');
});
