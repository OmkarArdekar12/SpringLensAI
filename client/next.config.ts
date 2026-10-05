import type { NextConfig } from "next";

const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(
  /\/$/,
  "",
);

if (process.env.VERCEL && !process.env.BACKEND_URL) {
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
  experimental: { proxyTimeout: 180_000 },

  async rewrites() {
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
