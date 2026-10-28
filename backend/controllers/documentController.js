import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { ingestDocument, getDocumentInventory, deleteDocument as removeDoc, getDocumentPreview } from '../services/documentService.js';
import { chromaClient } from '../database/chromaClient.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export async function uploadDocuments(req, res, next) {
  try {
    if (!req.files || req.files.length === 0) {
      return res.status(400).json({ error: 'No files provided for upload.' });
    }

    const results = [];
    let totalChunksIngested = 0;

    for (const file of req.files) {
      const result = await ingestDocument(file.buffer, file.originalname, file.mimetype);
      results.push(result);
      if (result.success && !result.isDuplicate) {
        totalChunksIngested += result.chunksIngested;
      }
    }

    const inventory = await getDocumentInventory();

    res.json({
      success: true,
      message: `Processed ${results.length} document(s).`,
      results,
      totalChunksIngested,
      inventory
    });
  } catch (err) {
    next(err);
  }
}

export async function listDocuments(req, res, next) {
  try {
    const inventory = await getDocumentInventory();
    res.json(inventory);
  } catch (err) {
    next(err);
  }
}

export async function deleteDocument(req, res, next) {
  try {
    const { id } = req.params;
    if (!id) {
      return res.status(400).json({ error: 'Document ID is required.' });
    }

    const result = await removeDoc(id);
    const updatedInventory = await getDocumentInventory();

    res.json({
      success: true,
      message: result.message,
      inventory: updatedInventory
    });
  } catch (err) {
    next(err);
  }
}

export async function previewDocument(req, res, next) {
  try {
    const { id } = req.params;
    const doc = getDocumentPreview(id);
    res.json(doc);
  } catch (err) {
    next(err);
  }
}

export async function loadSampleCorpus(req, res, next) {
  try {
    const samplesDir = path.join(__dirname, '../sample_docs');
    if (!fs.existsSync(samplesDir)) {
      return res.status(404).json({ error: 'Sample documents directory not found.' });
    }

    const files = fs.readdirSync(samplesDir).filter((f) => f.endsWith('.txt'));
    const loaded = [];

    for (const file of files) {
      const buffer = fs.readFileSync(path.join(samplesDir, file));
      const resIngest = await ingestDocument(buffer, file, 'text/plain');
      loaded.push(resIngest);
    }

    const inventory = await getDocumentInventory();

    res.json({
      success: true,
      message: `Loaded ${loaded.length} Indian legal sample document(s).`,
      loaded,
      inventory
    });
  } catch (err) {
    next(err);
  }
}

export async function resetCorpus(req, res, next) {
  try {
    await chromaClient.resetCollection();
    // Clear registry file
    const dataDir = path.join(__dirname, '../data');
    const metaFile = path.join(dataDir, 'documents.json');
    if (fs.existsSync(metaFile)) {
      fs.writeFileSync(metaFile, '[]', 'utf-8');
    }

    res.json({
      success: true,
      message: 'Knowledge base and vector database cleared successfully.'
    });
  } catch (err) {
    next(err);
  }
}
