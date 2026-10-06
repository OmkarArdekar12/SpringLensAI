import {
  ApiError,
  getApiBaseUrl,
  parseError,
  type ChatMessage,
} from "@/lib/api";

export type StreamChatHandlers = {
  onUserMessage?: (message: ChatMessage) => void;
  onToken?: (token: string) => void;
  onAssistantMessage?: (message: ChatMessage) => void;
  onDone?: () => void;
  signal?: AbortSignal;
};

export async function streamChatMessage(
  sessionId: string,
  content: string,
  handlers: StreamChatHandlers = {},
): Promise<void> {
  const res = await fetch(
    `${getApiBaseUrl()}/api/chat/sessions/${sessionId}/messages`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ content }),
      signal: handlers.signal,
    },
  );

  if (!res.ok) {
    throw new ApiError(res.status, await parseError(res));
  }
  if (!res.body) {
    throw new Error("No response body for SSE stream");
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let sawAssistantMessage = false;

  const handleEvent = (rawEvent: string) => {
    if (!rawEvent.trim()) return;

    let event = "message";
    const dataLines: string[] = [];
    for (const line of rawEvent.split(/\r?\n/)) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:"))
        dataLines.push(line.slice(5).trimStart());
    }
    const data = dataLines.join("\n");
    if (!data) return;

    if (event === "token") {
      // handlers.onToken?.(JSON.parse(data) as string);
      handlers.onToken?.(data);
    } else if (event === "user_message") {
      handlers.onUserMessage?.(JSON.parse(data) as ChatMessage);
    } else if (event === "assistant_message") {
      sawAssistantMessage = true;
      handlers.onAssistantMessage?.(JSON.parse(data) as ChatMessage);
    } else if (event === "error") {
      const payload = JSON.parse(data) as { message?: string };
      throw new Error(payload.message ?? "The AI service failed to respond.");
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split(/\r?\n\r?\n/);
    buffer = parts.pop() ?? "";
    for (const part of parts) handleEvent(part);
  }
  if (buffer.trim()) handleEvent(buffer);

  if (!sawAssistantMessage) {
    throw new Error(
      "The connection was interrupted before the answer finished.",
    );
  }
  handlers.onDone?.();
}
