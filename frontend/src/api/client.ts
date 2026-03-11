import axios, { AxiosError, AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import type { ApiEnvelope, AuthTokens } from "../types";

const API_BASE_KEY = "baicizhan_api_base_url";
const ACCESS_KEY = "baicizhan_access_token";
const REFRESH_KEY = "baicizhan_refresh_token";

const defaultBaseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
let accessToken = localStorage.getItem(ACCESS_KEY) || "";
let refreshToken = localStorage.getItem(REFRESH_KEY) || "";

export const client: AxiosInstance = axios.create({
  baseURL: localStorage.getItem(API_BASE_KEY) || defaultBaseUrl,
  timeout: 12000
});
const refreshClient: AxiosInstance = axios.create({
  baseURL: client.defaults.baseURL,
  timeout: 12000
});

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean };

let refreshing = false;
let pendingQueue: Array<{
  resolve: (value: AxiosResponse | PromiseLike<AxiosResponse>) => void;
  reject: (reason?: unknown) => void;
  config: RetryConfig;
}> = [];

client.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const original = error.config as RetryConfig;
    if (status !== 401 || !refreshToken || !original || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;

    if (refreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push({ resolve, reject, config: original });
      });
    }

    refreshing = true;
    try {
      const fresh = await refreshClient.post<ApiEnvelope<AuthTokens>>("/v1/auth/refresh", {
        refreshToken
      });
      if (fresh.data.code !== 0) {
        throw new Error(fresh.data.message || "TOKEN_REFRESH_FAILED");
      }
      setTokens(fresh.data.data);
      pendingQueue.forEach((entry) => {
        entry.config.headers.Authorization = `Bearer ${accessToken}`;
        entry.resolve(client(entry.config));
      });
      pendingQueue = [];
      original.headers.Authorization = `Bearer ${accessToken}`;
      return client(original);
    } catch (refreshError) {
      clearTokens();
      pendingQueue.forEach((entry) => entry.reject(refreshError));
      pendingQueue = [];
      return Promise.reject(refreshError);
    } finally {
      refreshing = false;
    }
  }
);

export function unwrap<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  if (!response.data) {
    throw new Error("EMPTY_RESPONSE");
  }
  if (response.data.code !== 0) {
    throw new Error(response.data.message || "REQUEST_FAILED");
  }
  return response.data.data;
}

export function setTokens(tokens: AuthTokens): void {
  accessToken = tokens.accessToken;
  refreshToken = tokens.refreshToken;
  localStorage.setItem(ACCESS_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function clearTokens(): void {
  accessToken = "";
  refreshToken = "";
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

export function isLoggedIn(): boolean {
  return Boolean(accessToken);
}

export function getApiBaseUrl(): string {
  return client.defaults.baseURL || defaultBaseUrl;
}

export function setApiBaseUrl(url: string): void {
  const normalized = (url || "").trim().replace(/\/+$/, "");
  client.defaults.baseURL = normalized || defaultBaseUrl;
  refreshClient.defaults.baseURL = client.defaults.baseURL;
  localStorage.setItem(API_BASE_KEY, client.defaults.baseURL as string);
}
