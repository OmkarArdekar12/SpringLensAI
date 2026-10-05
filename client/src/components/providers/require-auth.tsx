"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { isUnauthorized, useCurrentUser } from "@/hooks/use-auth";

/** Wraps pages that need a signed-in user. */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { data: user, isLoading, isError, error, refetch, failureCount } =
    useCurrentUser();
  const router = useRouter();
  const signedOut = isError && isUnauthorized(error);

  useEffect(() => {
    if (signedOut) {
      router.replace("/login");
    }
  }, [signedOut, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-muted-foreground">
          <Spinner className="size-6" />
          <p className="text-sm">
            {failureCount > 0
              ? "Waking up the server — this can take up to a minute…"
              : "Loading your workspace…"}
          </p>
        </div>
      </div>
    );
  }

  if (signedOut) return null;

  if (isError || !user) {
    return (
      <div className="flex min-h-svh flex-col items-center justify-center gap-3 p-6 text-center">
        <p className="font-medium">We couldn’t reach the server</p>
        <p className="max-w-sm text-sm text-muted-foreground">
          {(error as Error | null)?.message ?? "Please try again."}
        </p>
        <Button onClick={() => void refetch()}>Try again</Button>
      </div>
    );
  }

  return <>{children}</>;
}
