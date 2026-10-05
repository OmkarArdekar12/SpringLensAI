/** Single source of truth for brand + SEO values. Edit here, not in every page. */
function resolveSiteUrl() {
  const explicit = process.env.NEXT_PUBLIC_SITE_URL;
  if (explicit) return explicit.replace(/\/$/, "");
  // Vercel exposes the production domain at build time
  const vercel = process.env.VERCEL_PROJECT_PRODUCTION_URL;
  if (vercel) return `https://${vercel}`;
  return "http://localhost:3000";
}

export const siteConfig = {
  name: "SpringLens AI",
  shortName: "SpringLens",
  tagline: "Chat with your GitHub code",
  description:
    "SpringLens AI indexes your GitHub repositories and lets you ask questions about your code. Get grounded answers with clickable file and line citations, powered by Gemini and retrieval-augmented generation.",
  url: resolveSiteUrl(),
  keywords: [
    "chat with GitHub repository",
    "AI code assistant",
    "codebase Q&A",
    "RAG for code",
    "understand legacy code",
    "Gemini code search",
    "GitHub code explorer",
    "developer onboarding tool",
  ],
  twitter: "@springlensai",
  githubRepo: "https://github.com/",
} as const;
