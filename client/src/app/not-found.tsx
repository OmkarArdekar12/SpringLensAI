import Link from "next/link";

import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export default function NotFound() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-4 px-4 text-center">
      <p className="text-sm font-medium text-muted-foreground">404</p>
      <h1 className="font-heading text-3xl font-semibold tracking-tight">
        Page not found
      </h1>
      <p className="max-w-sm text-muted-foreground">
        The page you are looking for does not exist or has moved.
      </p>
      <Link href="/" className={cn(buttonVariants())}>
        Back to home
      </Link>
    </main>
  );
}
