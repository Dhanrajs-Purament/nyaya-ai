/**
 * Divides extracted document text into overlapping chunks while preserving page metadata and headings.
 * 
 * @param {string} fullText Raw extracted text (may contain [[PAGE_n]] markers)
 * @param {object} metadata Document metadata (document_id, filename, file_type, upload_timestamp)
 * @param {object} options Chunking configuration (chunkSize, chunkOverlap)
 */
export function chunkTextWithMetadata(fullText, metadata = {}, options = {}) {
  const { chunkSize = 800, chunkOverlap = 150 } = options;

  if (!fullText || !fullText.trim()) {
    return [];
  }

  // Parse page segments if markers exist
  const pageSegments = [];
  if (fullText.includes('[[PAGE_')) {
    const parts = fullText.split(/\[\[PAGE_(\d+)\]\]/);
    for (let i = 1; i < parts.length; i += 2) {
      const pageNum = parseInt(parts[i], 10);
      const pageContent = (parts[i + 1] || '').trim();
      if (pageContent) {
        pageSegments.push({ page_number: pageNum, text: pageContent });
      }
    }
  }

  // Fallback if no page markers are present (e.g., TXT files)
  if (pageSegments.length === 0) {
    pageSegments.push({
      page_number: metadata.file_type === 'pdf' ? 1 : null,
      text: fullText.trim()
    });
  }

  const chunks = [];
  let globalChunkIndex = 0;

  // Legal & statutory delimiters regex (Articles, Sections, Rules, Headnotes, Clauses)
  const structuralRegex = /(?:^|\n)(?=(?:(?:Article|Section|Sec\.|Art\.|Clause|Order|Rule)\s+\d+[A-Z\d\.\s-]*[:\.-]|\b(?:Held|Ratio Decidendi|Judgment|Issue|Conclusion|Summary|Background)[:\.-]))/im;

  for (const segment of pageSegments) {
    const cleanedText = segment.text
      .replace(/\r\n/g, '\n')
      .replace(/\t/g, ' ')
      .replace(/\n{3,}/g, '\n\n');

    const sections = cleanedText.split(structuralRegex);

    for (const rawSec of sections) {
      const section = rawSec.trim();
      if (!section) continue;

      // Extract section title or heading if present
      const headingMatch = section.match(/^(?:Article|Section|Sec\.|Art\.|Clause|Order|Rule)\s+([0-9A-Z\.\-]+)(?:[^\n]{0,80})/i);
      const heading = headingMatch ? headingMatch[0].trim() : (metadata.filename || 'General Context');

      if (section.length <= chunkSize) {
        globalChunkIndex++;
        const chunk_id = `${metadata.document_id || 'doc'}-chk-${globalChunkIndex}`;
        chunks.push({
          chunk_id,
          document_id: metadata.document_id,
          filename: metadata.filename,
          file_type: metadata.file_type,
          page_number: segment.page_number,
          chunk_index: globalChunkIndex,
          heading,
          text: section,
          char_count: section.length,
          upload_timestamp: metadata.upload_timestamp || new Date().toISOString()
        });
      } else {
        // Sliding window for longer sections
        let start = 0;
        while (start < section.length) {
          let end = start + chunkSize;
          if (end < section.length) {
            const periodIdx = section.lastIndexOf('. ', end);
            const newlineIdx = section.lastIndexOf('\n', end);
            const breakPoint = Math.max(periodIdx, newlineIdx);
            if (breakPoint > start + Math.floor(chunkSize * 0.6)) {
              end = breakPoint + 1;
            }
          } else {
            end = section.length;
          }

          const chunkContent = section.substring(start, end).trim();
          if (chunkContent.length > 25) {
            globalChunkIndex++;
            const chunk_id = `${metadata.document_id || 'doc'}-chk-${globalChunkIndex}`;
            chunks.push({
              chunk_id,
              document_id: metadata.document_id,
              filename: metadata.filename,
              file_type: metadata.file_type,
              page_number: segment.page_number,
              chunk_index: globalChunkIndex,
              heading,
              text: chunkContent,
              char_count: chunkContent.length,
              upload_timestamp: metadata.upload_timestamp || new Date().toISOString()
            });
          }

          if (end >= section.length) break;
          start = end - chunkOverlap;
        }
      }
    }
  }

  return chunks;
}
