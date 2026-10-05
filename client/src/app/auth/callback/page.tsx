"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Spinner } from "@/components/ui/spinner";
import { useCurrentUser } from "@/hooks/use-auth";

export default function AuthCallbackPage() {
  const router = useRouter();
  const { data: user, isLoading, isFetched } = useCurrentUser();

  useEffect(() => {
    if (!isFetched || isLoading) return;
    router.replace(user ? "/dashboard" : "/login?error=session");
  }, [user, isLoading, isFetched, router]);

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-3">
      <Spinner className="size-6" />
      <p className="text-sm text-muted-foreground">
        Finishing GitHub sign-in...
      </p>
    </div>
  );
}
