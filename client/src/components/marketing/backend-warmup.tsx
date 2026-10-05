"use client";

import { useEffect } from "react";

import { api } from "@/lib/api";

export function BackendWarmup() {
  useEffect(() => {
    api.health().catch(() => undefined);
  }, []);
  return null;
}
