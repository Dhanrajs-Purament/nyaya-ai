import multer from 'multer';

// Multer memory storage with file validation
const storage = multer.memoryStorage();

function fileFilter(req, file, cb) {
  const isPdf = file.mimetype === 'application/pdf' || file.originalname.toLowerCase().endsWith('.pdf');
  const isTxt = file.mimetype === 'text/plain' || file.originalname.toLowerCase().endsWith('.txt');

  if (isPdf || isTxt) {
    cb(null, true);
  } else {
    cb(new Error(`Unsupported file type: ${file.originalname}. Only PDF and TXT files are supported.`), false);
  }
}

export const uploadMiddleware = multer({
  storage,
  fileFilter,
  limits: {
    fileSize: 30 * 1024 * 1024 // 30 MB per file limit
  }
});
