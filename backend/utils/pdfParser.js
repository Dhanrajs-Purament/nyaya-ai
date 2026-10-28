import pdfParse from 'pdf-parse';

/**
 * Extracts text from a PDF buffer, injecting [[PAGE_n]] markers for page boundaries.
 */
export async function parsePdfWithPages(buffer) {
  try {
    const options = {
      pagerender: async function (pageData) {
        const textContent = await pageData.getTextContent({
          normalizeWhitespace: true,
          disableCombineTextItems: false
        });
        let pageText = '';
        for (const item of textContent.items) {
          pageText += item.str + ' ';
        }
        return `\n[[PAGE_${pageData.pageIndex + 1}]]\n` + pageText;
      }
    };

    const data = await pdfParse(buffer, options);
    return {
      text: data.text || '',
      numPages: data.numpages || 1,
      info: data.info || {}
    };
  } catch (err) {
    // Graceful fallback to default pdf-parse if custom page renderer encounters an internal stream error
    console.warn('PDF stream pagerender fallback:', err.message);
    const data = await pdfParse(buffer);
    return {
      text: data.text || '',
      numPages: data.numpages || 1,
      info: data.info || {}
    };
  }
}

/**
 * Parses a TXT buffer.
 */
export function parseTxt(buffer) {
  const text = buffer.toString('utf-8');
  return {
    text,
    numPages: 1,
    info: {}
  };
}
