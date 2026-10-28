import crypto from 'crypto';

export function computeFileHash(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

export function validateQuestion(question) {
  if (!question || typeof question !== 'string' || !question.trim()) {
    throw new Error('A valid, non-empty question string is required.');
  }
  return question.trim();
}

export function validateModelChoice(model, availableModels = []) {
  if (!model || typeof model !== 'string') {
    return 'gemma3:4b'; // Default model
  }
  return model.trim();
}
