import type { NextConfig } from "next";

/**
 * URL of the Spring Boot server (Render in production, localhost in development).
 * Set BACKEND_URL in Vercel -> Project Settings -> Environment Variables.
 */
const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(
  /\/$/,
  "",
);

if (process.env.VERCEL && !process.env.BACKEND_URL) {
  // Fail the Vercel build loudly instead of deploying a site that proxies to localhost
  throw new Error(
    "BACKEND_URL is not set. Add it in Vercel (e.g. https://springlensai-server.onrender.com) and redeploy.",
  );
}

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "SAMEORIGIN" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=()",
  },
  {
    key: "Strict-Transport-Security",
    value: "max-age=63072000; includeSubDomains",
  },
];

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // Chat answers stream for up to ~2 minutes; the default proxy timeout (30s) would cut them off
  experimental: { proxyTimeout: 180_000 },

  async rewrites() {
    /**
     * The browser only talks to THIS origin. These rewrites forward API + OAuth traffic to Spring,
     * which makes the session cookie first-party (works in Safari / Chrome with third-party
     * cookies blocked) and means no CORS is needed. They run before pages, so they win over routes.
     */
    return {
      beforeFiles: [
        { source: "/api/:path*", destination: `${backendUrl}/api/:path*` },
        {
          source: "/oauth2/:path*",
          destination: `${backendUrl}/oauth2/:path*`,
        },
        {
          source: "/login/oauth2/:path*",
          destination: `${backendUrl}/login/oauth2/:path*`,
        },
      ],
    };
  },

  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
