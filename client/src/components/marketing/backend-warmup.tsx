"use client";

import { useEffect } from "react";

import { api } from "@/lib/api";

/**
 * Pings the API once when the landing page opens. On Render's free plan the server sleeps after
 * 15 minutes; this wakes it while the visitor reads, so "Sign in" feels instant.
 * Renders nothing and ignores all errors.
 */
export function BackendWarmup() {
  useEffect(() => {
    api.health().catch(() => undefined);
  }, []);
  return null;
}
