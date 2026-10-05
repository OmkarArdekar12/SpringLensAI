"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect } from "react";
import { AlertCircle } from "lucide-react";

import { GitHubIcon } from "@/components/icons/github-icon";
import { BrandMark } from "@/components/layout/brand-mark";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ModeToggle } from "@/components/ui/mode-toggle";
import { Spinner } from "@/components/ui/spinner";
import { useCurrentUser } from "@/hooks/use-auth";
import { getGithubLoginUrl } from "@/lib/api";
import { cn } from "@/lib/utils";

function LoginLoading() {
  return (
    <div className="flex min-h-svh items-center justify-center">
      <Spinner className="size-8" />
    </div>
  );
}

function LoginContent() {
  const params = useSearchParams();
  const router = useRouter();
  const error = params.get("error");
  const next = params.get("next") || "/dashboard";
  const { data: user, isLoading } = useCurrentUser();

  useEffect(() => {
    if (!isLoading && user) {
      router.replace(
        next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard",
      );
    }
  }, [user, isLoading, next, router]);

  return (
    <div className="relative flex min-h-svh flex-col overflow-hidden bg-background">
      <header className="relative z-10 flex h-14 items-center justify-between px-4">
        <Link href="/">
          <BrandMark />
        </Link>
        <ModeToggle />
      </header>

      <main className="relative z-10 flex flex-1 items-center justify-center px-4 py-10">
        <Card className="w-full max-w-sm">
          <CardHeader className="space-y-4 text-center">
            <div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-foreground text-background">
              <GitHubIcon className="size-6" />
            </div>
            <div className="space-y-1">
              <CardTitle className="text-xl">Sign in</CardTitle>
              <CardDescription>
                Connect GitHub to chat with your repositories.
              </CardDescription>
            </div>
          </CardHeader>

          <CardContent className="space-y-4">
            {error && (
              <Alert variant="destructive">
                <AlertCircle />
                <AlertTitle>Sign-in failed</AlertTitle>
                <AlertDescription>
                  {error === "session"
                    ? "We could not confirm your session. Please sign in again."
                    : "GitHub sign-in did not complete. Please try again."}
                </AlertDescription>
              </Alert>
            )}

            <a
              href={getGithubLoginUrl()}
              className={cn(
                buttonVariants({ size: "lg" }),
                "inline-flex w-full items-center justify-center gap-2 bg-foreground text-background hover:bg-foreground/90",
              )}
            >
              <GitHubIcon className="size-5" />
              Continue with GitHub
            </a>
            <p className="text-center text-xs text-muted-foreground">
              We request read access to your profile and repositories so we can
              index the ones you choose.
            </p>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<LoginLoading />}>
      <LoginContent />
    </Suspense>
  );
}
