import { client, unwrap } from "./client";
import type {
  AiJob,
  AiJobAccepted,
  AuthTokens,
  ReviewCard,
  ReviewResult,
  TermDetail,
  VocabItemCreateResult,
  VocabList
} from "../types";

export async function register(payload: {
  email: string;
  password: string;
  displayName: string;
}): Promise<AuthTokens> {
  const res = await client.post("/v1/auth/register", payload);
  return unwrap<AuthTokens>(res);
}

export async function login(payload: { email: string; password: string }): Promise<AuthTokens> {
  const res = await client.post("/v1/auth/login", payload);
  return unwrap<AuthTokens>(res);
}

export async function getLists(): Promise<VocabList[]> {
  const res = await client.get("/v1/lists");
  return unwrap<VocabList[]>(res);
}

export async function createList(payload: {
  name: string;
  sourceLanguage: string;
  targetLanguage: string;
  isPublic: boolean;
}): Promise<VocabList> {
  const res = await client.post("/v1/lists", payload);
  return unwrap<VocabList>(res);
}

export async function addListItem(
  listId: number,
  payload: {
    text: string;
    partOfSpeech?: string;
    definition?: string;
    translation?: string;
    example?: string;
    ipa?: string;
    audioUrl?: string;
  }
): Promise<VocabItemCreateResult> {
  const res = await client.post(`/v1/lists/${listId}/items`, payload);
  return unwrap<VocabItemCreateResult>(res);
}

export async function getTermDetail(termId: number): Promise<TermDetail> {
  const res = await client.get(`/v1/terms/${termId}`);
  return unwrap<TermDetail>(res);
}

export async function getReviewCards(limit = 20): Promise<ReviewCard[]> {
  const res = await client.get("/v1/review/next", { params: { limit } });
  return unwrap<ReviewCard[]>(res);
}

export async function submitReview(
  termId: number,
  payload: { rating: number; elapsedMs?: number }
): Promise<ReviewResult> {
  const res = await client.post(`/v1/review/${termId}/result`, payload);
  return unwrap<ReviewResult>(res);
}

export async function createAiEnrich(payload: {
  termId: number;
  targetLang?: string;
}): Promise<AiJobAccepted> {
  const res = await client.post("/v1/ai/enrich", payload);
  return unwrap<AiJobAccepted>(res);
}

export async function getAiJob(jobId: number): Promise<AiJob> {
  const res = await client.get(`/v1/ai/jobs/${jobId}`);
  return unwrap<AiJob>(res);
}
