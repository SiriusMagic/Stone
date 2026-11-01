import { storage } from "@/src/utils/storage";

const FEEDBACK_KEY = "@app/stone-feedback";
export type FeedbackEntry = { id: string; category: string; details: string; createdAt: number; attachments: string[] };

export async function loadFeedback() {
  return (await storage.getItem<FeedbackEntry[]>(FEEDBACK_KEY, [])) ?? [];
}

export async function addFeedback(entry: Omit<FeedbackEntry, "id" | "createdAt">) {
  const items = await loadFeedback();
  const next = [{ ...entry, id: `${Date.now()}`, createdAt: Date.now() }, ...items];
  await storage.setItem(FEEDBACK_KEY, next);
  return next;
}
