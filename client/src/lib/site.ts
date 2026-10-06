function resolveSiteUrl() {
  const explicit = process.env.NEXT_PUBLIC_SITE_URL;
  if (explicit) {
    return explicit.replace(/\/$/, "");
  }
  return "http://localhost:3000";
}

export const siteConfig = {
  name: "SpringLensAI",
  shortName: "SpringLensAI",
  tagline: "Chat with your code",
  description:
    "SpringLensAI indexes your GitHub repositories and lets you ask questions about your code. Get grounded answers with clickable file and line citations using retrieval-augmented generation.",
  url: resolveSiteUrl(),
  keywords: [
    "SpringLensAI",
    "SpringLens AI",
    "chat with GitHub repository",
    "AI code assistant",
    "codebase Q&A",
    "RAG for code",
    "understand legacy code",
    "Gemini code search",
    "GitHub code explorer",
    "developer onboarding tool",
    "CodeElevate Community",
    "Omkar Ardekar",
  ],
  twitter: "",
  githubRepo: "https://github.com/OmkarArdekar12/SpringLensAI",
} as const;
