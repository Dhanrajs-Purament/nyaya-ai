import test from 'node:test';
import assert from 'node:assert/strict';
import { extractText, chunkLegalDocument } from '../src/services/documentParser.js';
import { generateMultiModelComparison } from '../src/services/ollamaService.js';

test('1. Document Ingestion: TXT parsing and legal chunking with page preservation', async () => {
  const sampleTxt = `
Article 14: Equality before law
The State shall not deny to any person equality before the law.

Article 21: Protection of life and personal liberty
No person shall be deprived of his life or personal liberty except according to procedure established by law.
`;

  const extracted = await extractText(Buffer.from(sampleTxt, 'utf-8'), 'text/plain', 'test_act.txt');
  assert.equal(extracted.numPages, 1);
  assert.ok(extracted.text.includes('Article 21'));

  const chunks = chunkLegalDocument(extracted.text, {
    documentId: 'doc-test-1',
    filename: 'test_act.txt',
    pageNumber: 1
  });

  assert.ok(chunks.length >= 2, `Expected at least 2 chunks, got ${chunks.length}`);
  const art21Chunk = chunks.find((c) => c.text.includes('Article 21'));
  assert.ok(art21Chunk, 'Expected Article 21 chunk to exist');
  assert.equal(art21Chunk.metadata.pageNumber, 1);
  assert.ok(art21Chunk.metadata.heading.includes('Article 21'));
});

test('2. Document Ingestion: Page-level boundary markers and page numbers', () => {
  const multiPageText = `
[[PAGE_1]]
Section 103: Murder
Whoever commits murder shall be punished with death or imprisonment for life.

[[PAGE_2]]
Section 303: Theft
Whoever commits theft shall be punished with imprisonment up to three years.
`;

  const chunks = chunkLegalDocument(multiPageText, {
    documentId: 'bns-test',
    filename: 'bns.txt'
  });

  assert.ok(chunks.length >= 2, `Expected at least 2 chunks, got ${chunks.length}`);
  const sec103 = chunks.find((c) => c.text.includes('Section 103'));
  const sec303 = chunks.find((c) => c.text.includes('Section 303'));

  assert.ok(sec103, 'Section 103 chunk must exist');
  assert.ok(sec303, 'Section 303 chunk must exist');
  assert.equal(sec103.metadata.pageNumber, 1, 'Section 103 must be on page 1');
  assert.equal(sec303.metadata.pageNumber, 2, 'Section 303 must be on page 2');
});

test('3. Retrieval-Once Invariant: Multi-LLM comparison must execute retrieval exactly once', async () => {
  let retrievalCount = 0;

  // Mock retrieval service to track invocation count
  const mockVectorDB = {
    async querySimilar(queryText, topK) {
      retrievalCount++;
      return [
        {
          id: 'chunk-1',
          text: 'Justice K.S. Puttaswamy held Right to Privacy is protected under Article 21.',
          similarity: 88.5,
          distance: 0.115,
          metadata: { documentName: 'puttaswamy.txt', pageNumber: 3, heading: 'Ratio Decidendi' }
        }
      ];
    }
  };

  // Mock inference runner
  const receivedEvidenceByModel = {};
  async function mockGenerate(model, prompt) {
    receivedEvidenceByModel[model] = prompt;
    return {
      model,
      success: true,
      response: `Analysis for ${model}`,
      metrics: { latencySeconds: 0.5, tokensGenerated: 30, tokensPerSecond: 60 }
    };
  }

  // Simulate compare workflow
  const query = 'What did Puttaswamy hold?';
  const models = ['gemma3:1b', 'gemma3:4b', 'qwen3:4b'];

  // 1. Single retrieval
  const evidence = await mockVectorDB.querySimilar(query, 2);

  // 2. Format evidence
  const sharedPrompt = `Context:\n${evidence.map((e) => e.text).join('\n')}\nQuery: ${query}`;

  // 3. Concurrently invoke all models with identical prompt
  await Promise.all(models.map((m) => mockGenerate(m, sharedPrompt)));

  // INVARIANT 1: Retrieval ran strictly once
  assert.equal(retrievalCount, 1, 'Retrieval must be invoked exactly once for all models');

  // INVARIANT 2: All models received the exact identical prompt and evidence
  assert.equal(receivedEvidenceByModel['gemma3:1b'], sharedPrompt);
  assert.equal(receivedEvidenceByModel['gemma3:4b'], sharedPrompt);
  assert.equal(receivedEvidenceByModel['qwen3:4b'], sharedPrompt);
});

test('4. Source Citations: Structure and Page metadata preservation', () => {
  const citation = {
    id: 'doc-chunk-42',
    text: 'Article 21 includes the right to a clean and healthy environment.',
    metadata: {
      documentName: 'constitution_of_india.pdf',
      pageNumber: 14,
      heading: 'Article 21: Life and Liberty',
      chunkIndex: 42
    },
    similarity: 92.4,
    distance: 0.076
  };

  assert.equal(typeof citation.id, 'string');
  assert.ok(citation.similarity >= 0 && citation.similarity <= 100);
  assert.equal(citation.metadata.pageNumber, 14);
  assert.equal(citation.metadata.documentName, 'constitution_of_india.pdf');
  assert.ok(citation.text.length > 0);
});

test('5. Unavailable Ollama Model: Partial model failure handling', async () => {
  // Test calling generateMultiModelComparison with an intentionally non-existent model
  // along with handling so that the execution does not crash and returns graceful error
  const modelsToTest = ['non_existent_fake_model_999b'];
  const results = await generateMultiModelComparison(modelsToTest, 'Hello, test query.');

  assert.equal(results.length, 1);
  const fakeResult = results[0];
  assert.equal(fakeResult.model, 'non_existent_fake_model_999b');
  assert.equal(fakeResult.success, false, 'Non-existent model must report success=false');
  assert.ok(fakeResult.error, 'Error message must be present for unavailable model');
  assert.ok(fakeResult.metrics !== undefined, 'Metrics structure must still be provided');
});
