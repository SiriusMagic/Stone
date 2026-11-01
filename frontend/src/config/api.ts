import Constants from "expo-constants";
import { NativeModules, Platform } from "react-native";

const DEFAULT_API_BASE_URL = "http://127.0.0.1:8000/api";

function trimApiSuffix(value: string) {
  return value.replace(/\/+$/, "").replace(/\/api$/, "");
}

function getExpoDevHost() {
  const sourceUrl = NativeModules?.SourceCode?.scriptURL ?? "";
  if (sourceUrl) {
    try {
      const { hostname } = new URL(sourceUrl);
      if (hostname) return hostname;
    } catch {}
  }

  const hostUri =
    Constants.expoConfig?.hostUri ||
    (typeof Constants.linkingUri === "string" ? Constants.linkingUri : "");

  if (!hostUri) return "";

  const normalized = hostUri.includes("://") ? hostUri : `exp://${hostUri}`;

  try {
    const { hostname } = new URL(normalized);
    return hostname || "";
  } catch {
    return "";
  }
}

export function getApiBaseUrl() {
  const envUrl =
    process.env.EXPO_PUBLIC_API_BASE_URL ||
    Constants.expoConfig?.extra?.apiBaseUrl ||
    "";

  if (envUrl) return `${trimApiSuffix(envUrl)}/api`;

  if (Platform.OS === "web" && typeof window !== "undefined" && window.location?.hostname) {
    const host = window.location.hostname;
    const apiHost = host === "localhost" || host === "::1" ? "127.0.0.1" : host;
    return `${window.location.protocol}//${apiHost}:8000/api`;
  }

  const expoDevHost = getExpoDevHost();
  if (expoDevHost && expoDevHost !== "localhost" && expoDevHost !== "::1") {
    return `http://${expoDevHost}:8000/api`;
  }

  return DEFAULT_API_BASE_URL;
}

export function getApiOrigin() {
  return trimApiSuffix(getApiBaseUrl());
}
