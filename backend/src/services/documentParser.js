import pdfParse from 'pdf-parse';

/**
 * Extract clean raw text from PDF or TXT buffer, preserving page boundary markers.
 */
export async function extractText(fileBuffer, mimeType, filename) {
  const isPdf = mimeType === 'application/pdf' || filename.toLowerCase().endsWith('.pdf');
  const isTxt = mimeType === 'text/plain' || filename.toLowerCase().endsWith('.txt');

  if (isPdf) {
    try {
      const options = {
        pagerender: function (pageData) {
          return pageData.getTextContent({
            normalizeWhitespace: true,
            disableCombineTextItems: false
          }).then(function (textContent) {
            let text = '';
            for (const item of textContent.items) {
              text += item.str + ' ';
            }
            return `\n[[PAGE_${pageData.pageIndex + 1}]]\n` + text;
          });
        }
      };

      const data = await pdfParse(fileBuffer, options);
      return {
        text: data.text || '',
        numPages: data.numpages || 1,
        info: data.info || {}
      };
    } catch (parseErr) {
      // Fallback to standard pdfParse without custom pagerender if special stream parsing fails
      console.warn('PDF custom pagerender fallback:', parseErr.message);
      const data = await pdfParse(fileBuffer);
      return {
        text: data.text || '',
        numPages: data.numpages || 1,
        info: data.info || {}
      };
    }
  } else if (isTxt) {
    const text = fileBuffer.toString('utf-8');
    return {
      text,
      numPages: 1,
      info: {}
    };
  } else {
    throw new Error(`Unsupported document format. Only PDF and TXT are supported (received ${mimeType || filename})`);
  }
}

/**
 * Intelligent chunker tailored for Indian legal documents:
 * Preserves Articles, Sections, Clauses, Judgment Headnotes, and PDF page numbers.
 */
export function chunkLegalDocument(fullText, metadata = {}, options = {}) {
  const { chunkSize = 750, chunkOverlap = 120 } = options;
  if (!fullText || !fullText.trim()) {
    return [];
  }

  // Check if text has page markers
  const pageSegments = [];
  if (fullText.includes('[[PAGE_')) {
    const parts = fullText.split(/\[\[PAGE_(\d+)\]\]/);
    // parts[0] is text before first page marker (usually empty/whitespace)
    for (let i = 1; i < parts.length; i += 2) {
      const pageNum = parseInt(parts[i], 10);
      const pageText = (parts[i + 1] || '').trim();
      if (pageText) {
        pageSegments.push({ pageNumber: pageNum, text: pageText });
      }
    }
  }

  // If no page markers detected, treat entire text as single segment with metadata.pageNumber or 1
  if (pageSegments.length === 0) {
    pageSegments.push({
      pageNumber: metadata.pageNumber || 1,
      text: fullText.trim()
    });
  }

  const chunks = [];
  let chunkIndex = 0;

  // Regex for legal section delimiters: Article, Section, Clause, Order, Rule, Paragraph, Headnote
  const legalSectionRegex = /(?:^|\n)(?=(?:(?:Article|Section|Sec\.|Art\.|Clause|Order|Rule)\s+\d+[A-Z\d\.\s-]*[:\.-]|\b(?:Held|Ratio Decidendi|Judgment|Issue|Conclusion)[:\.-]))/im;

  for (const segment of pageSegments) {
    const cleanedText = segment.text.replace(/\r\n/g, '\n').replace(/\t/g, ' ').replace(/\n{3,}/g, '\n\n');
    const rawSections = cleanedText.split(legalSectionRegex);

    for (const rawSec of rawSections) {
      const section = rawSec.trim();
      if (!section) continue;

      // Detect section heading if present
      const headingMatch = section.match(/^(?:Article|Section|Sec\.|Art\.|Clause|Order|Rule)\s+([0-9A-Z\.\-]+)(?:[^\n]{0,80})/i);
      const detectedHeading = headingMatch ? headingMatch[0].trim() : (metadata.title || 'General Legal Context');

      if (section.length <= chunkSize) {
        chunkIndex++;
        chunks.push({
          id: `${metadata.documentId || 'doc'}-chunk-${chunkIndex}`,
          text: section,
          metadata: {
            ...metadata,
            chunkIndex,
            pageNumber: segment.pageNumber,
            heading: detectedHeading,
            length: section.length
          }
        });
      } else {
        // Sliding window over longer sections
        let start = 0;
        while (start < section.length) {
          let end = start + chunkSize;
          if (end < section.length) {
            const periodIndex = section.lastIndexOf('. ', end);
            const newlineIndex = section.lastIndexOf('\n', end);
            const breakPoint = Math.max(periodIndex, newlineIndex);
            if (breakPoint > start + Math.floor(chunkSize * 0.6)) {
              end = breakPoint + 1;
            }
          } else {
            end = section.length;
          }

          const chunkText = section.substring(start, end).trim();
          if (chunkText.length > 30) {
            chunkIndex++;
            chunks.push({
              id: `${metadata.documentId || 'doc'}-chunk-${chunkIndex}`,
              text: chunkText,
              metadata: {
                ...metadata,
                chunkIndex,
                pageNumber: segment.pageNumber,
                heading: detectedHeading,
                length: chunkText.length
              }
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
