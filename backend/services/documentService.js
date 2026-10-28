import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parsePdfWithPages, parseTxt } from '../utils/pdfParser.js';
import { chunkTextWithMetadata } from '../utils/chunkText.js';
import { computeFileHash } from '../utils/validators.js';
import { generateDocumentEmbeddings } from './embeddingService.js';
import { chromaClient } from '../database/chromaClient.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DATA_DIR = path.join(__dirname, '../data');
const METADATA_FILE = path.join(DATA_DIR, 'documents.json');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function loadRegistry() {
  if (fs.existsSync(METADATA_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(METADATA_FILE, 'utf-8'));
    } catch {
      return [];
    }
  }
  return [];
}

function saveRegistry(docs) {
  fs.writeFileSync(METADATA_FILE, JSON.stringify(docs, null, 2), 'utf-8');
}

/**
 * Ingests an uploaded document (PDF or TXT), chunks it, generates embeddings, and saves to ChromaDB.
 */
export async function ingestDocument(fileBuffer, originalname, mimetype, options = {}) {
  const fileHash = computeFileHash(fileBuffer);
  const registry = loadRegistry();

  // 1. Duplicate detection via SHA-256 hash
  const existing = registry.find((d) => d.fileHash === fileHash);
  if (existing) {
    return {
      isDuplicate: true,
      message: `Document "${originalname}" is already indexed.`,
      document: existing
    };
  }

  const isPdf = mimetype === 'application/pdf' || originalname.toLowerCase().endsWith('.pdf');
  const file_type = isPdf ? 'pdf' : 'txt';

  // 2. Extract text & page boundaries
  const extracted = isPdf ? await parsePdfWithPages(fileBuffer) : parseTxt(fileBuffer);
  const document_id = `doc_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
  const upload_timestamp = new Date().toISOString();

  // 3. Chunk text with page preservation
  const chunks = chunkTextWithMetadata(
    extracted.text,
    {
      document_id,
      filename: originalname,
      file_type,
      upload_timestamp
    },
    {
      chunkSize: options.chunkSize || 800,
      chunkOverlap: options.chunkOverlap || 150
    }
  );

  if (chunks.length === 0) {
    throw new Error(`No extractable text found in "${originalname}".`);
  }

  // 4. Generate embeddings via nomic-embed-text
  const texts = chunks.map((c) => c.text);
  const embeddings = await generateDocumentEmbeddings(texts);

  // 5. Store in ChromaDB
  const collection = await chromaClient.getCollection();
  const ids = chunks.map((c) => c.chunk_id);
  const metadatas = chunks.map((c) => ({
    document_id: String(c.document_id),
    documentId: String(c.document_id), // Backward-compatible alias
    filename: String(c.filename),
    documentName: String(c.filename), // Backward-compatible alias
    file_type: String(c.file_type),
    page_number: c.page_number !== null ? Number(c.page_number) : 1,
    pageNumber: c.page_number !== null ? Number(c.page_number) : 1, // Backward-compatible alias
    chunk_index: Number(c.chunk_index),
    heading: String(c.heading || ''),
    upload_timestamp: String(c.upload_timestamp)
  }));

  await collection.add({
    ids,
    embeddings,
    documents: texts,
    metadatas
  });

  // 6. Record metadata in registry
  const docRecord = {
    document_id,
    filename: originalname,
    file_type,
    fileHash,
    sizeBytes: fileBuffer.length,
    numPages: extracted.numPages || 1,
    chunksCount: chunks.length,
    upload_timestamp,
    previewSnippet: extracted.text.substring(0, 400).trim()
  };

  registry.unshift(docRecord);
  saveRegistry(registry);

  return {
    isDuplicate: false,
    success: true,
    document: docRecord,
    chunksIngested: chunks.length
  };
}

/**
 * Returns knowledge base inventory and stats.
 */
export async function getDocumentInventory() {
  const registry = loadRegistry();
  const collection = await chromaClient.getCollection();
  const totalChunks = await collection.count().catch(() => 0);

  const totalBytes = registry.reduce((acc, d) => acc + (d.sizeBytes || 0), 0);

  const normalized = registry.map((d) => ({
    ...d,
    id: d.document_id || d.id || `doc_${Date.now()}`,
    document_id: d.document_id || d.id,
    documentId: d.document_id || d.id,
    name: d.filename || d.name || 'Legal Document',
    filename: d.filename || d.name || 'Legal Document',
    numPages: d.numPages || d.pageCount || 1,
    pageCount: d.numPages || d.pageCount || 1,
    chunksCount: d.chunksCount || d.chunkCount || 1,
    chunkCount: d.chunksCount || d.chunkCount || 1,
    uploadedAt: d.upload_timestamp || d.uploadedAt || new Date().toISOString()
  }));

  return {
    totalDocuments: registry.length,
    totalChunks,
    totalBytesIndexed: totalBytes,
    documents: normalized
  };
}

/**
 * Deletes a document and its associated vectors from ChromaDB.
 */
export async function deleteDocument(documentId) {
  const registry = loadRegistry();
  const targetIdx = registry.findIndex(
    (d) =>
      d.document_id === documentId ||
      d.id === documentId ||
      d.documentId === documentId ||
      d.filename === documentId
  );

  if (targetIdx === -1) {
    throw new Error(`Document with ID "${documentId}" not found.`);
  }

  const [removedDoc] = registry.splice(targetIdx, 1);
  saveRegistry(registry);

  const actualId = removedDoc.document_id || removedDoc.id;

  // Remove associated vectors from ChromaDB
  try {
    const collection = await chromaClient.getCollection();
    await collection.delete({
      where: { document_id: actualId }
    });
  } catch (chromaErr) {
    console.warn(`Vector cleanup notice for ${actualId}:`, chromaErr.message);
  }

  return {
    success: true,
    message: `Document "${removedDoc.filename}" and its vectors deleted successfully.`,
    removedDoc
  };
}

/**
 * Returns document preview content.
 */
export function getDocumentPreview(documentId) {
  const registry = loadRegistry();
  const doc = registry.find(
    (d) =>
      d.document_id === documentId ||
      d.id === documentId ||
      d.documentId === documentId ||
      d.filename === documentId
  );
  if (!doc) {
    throw new Error(`Document with ID "${documentId}" not found.`);
  }
  return {
    ...doc,
    id: doc.document_id || doc.id,
    document_id: doc.document_id || doc.id,
    documentId: doc.document_id || doc.id,
    name: doc.filename || doc.name,
    filename: doc.filename || doc.name,
    pageCount: doc.numPages || doc.pageCount || 1,
    numPages: doc.numPages || doc.pageCount || 1,
    chunkCount: doc.chunksCount || doc.chunkCount || 1,
    chunksCount: doc.chunksCount || doc.chunkCount || 1
  };
}

