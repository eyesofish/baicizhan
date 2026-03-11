export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface VocabList {
  id: number;
  name: string;
  sourceLanguage: string;
  targetLanguage: string;
  isPublic: boolean;
  itemCount: number;
  updatedAt: string;
}

export interface VocabItemCreateResult {
  listId: number;
  itemId: number;
  termId: number;
  senseId?: number | null;
}

export interface Translation {
  id: number;
  targetLanguage: string;
  translatedText: string;
  sourceType: string;
}

export interface ExampleSentence {
  id: number;
  language: string;
  sentenceText: string;
  sentenceTrans?: string | null;
  sourceType: string;
}

export interface Sense {
  id: number;
  partOfSpeech?: string | null;
  definition?: string | null;
  translations: Translation[];
  examples: ExampleSentence[];
}

export interface TermDetail {
  id: number;
  text: string;
  normalizedText: string;
  ipa?: string | null;
  audioUrl?: string | null;
  language: string;
  updatedAt: string;
  senses: Sense[];
}

export interface ReviewCard {
  termId: number;
  text: string;
  language: string;
  easeFactor: number;
  intervalDays: number;
  repetition: number;
  nextReviewAt: string;
}

export interface ReviewResult {
  termId: number;
  rating: number;
  easeFactor: number;
  intervalDays: number;
  repetition: number;
  nextReviewAt: string;
}

export interface AiJobAccepted {
  jobId: number;
}

export interface AiJob {
  jobId: number;
  status: string;
  termId?: number | null;
  openaiResponseId?: string | null;
  requestJson: string;
  resultJson?: string | null;
  errorMessage?: string | null;
  createdAt: string;
  updatedAt: string;
}
