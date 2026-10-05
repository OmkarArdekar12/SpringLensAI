"use client";

import { Button } from "@/components/shadcn-ui/button";

export default function Error({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="font-heading text-2xl font-semibold tracking-tight">
        Something went wrong
      </h1>
      <p className="max-w-sm text-muted-foreground">
        An unexpected error occurred. You can try again, and if it keeps
        happening, reload the page.
      </p>
      <Button onClick={reset}>Try again</Button>
    </main>
  );
}
