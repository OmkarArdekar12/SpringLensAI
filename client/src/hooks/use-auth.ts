"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";

import { api, ApiError } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { toast } from "@/components/shadcn-ui/toast";

export const AUTH_COOKIE = "springlens_auth";

export function setAuthCookie(authed: boolean) {
  if (typeof document === "undefined") return;
  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  if (authed) {
    document.cookie = `${AUTH_COOKIE}=1; path=/; max-age=${60 * 60 * 24 * 7}; SameSite=Lax${secure}`;
  } else {
    document.cookie = `${AUTH_COOKIE}=; path=/; max-age=0; SameSite=Lax${secure}`;
  }
}

export function isUnauthorized(error: unknown) {
  return error instanceof ApiError && error.status === 401;
}

export function useCurrentUser() {
  return useQuery({
    queryKey: queryKeys.auth.me(),
    queryFn: async () => {
      try {
        const user = await api.me();
        setAuthCookie(true);
        return user;
      } catch (error) {
        if (isUnauthorized(error)) setAuthCookie(false);
        throw error;
      }
    },
    staleTime: 5 * 60 * 1000,
    retry: (failureCount, error) => !isUnauthorized(error) && failureCount < 4,
    retryDelay: (attempt) => Math.min(2000 * 2 ** attempt, 15000),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();

  return useMutation({
    mutationFn: () => api.logout(),
    onSettled: async () => {
      setAuthCookie(false);
      queryClient.clear();
      router.replace("/login");
    },
  });
}

export function useDeleteAccount() {
  const queryClient = useQueryClient();
  const router = useRouter();

  return useMutation({
    mutationFn: (confirmation: string) => api.deleteAccount(confirmation),
    onSuccess: () => {
      setAuthCookie(false);
      queryClient.clear();
      toast.add({
        title: "Account deleted",
        description:
          "Your account and all of your data have been permanently removed.",
        type: "success",
      });
      router.replace("/");
    },
    onError: (error: Error) => {
      toast.add({
        title: "Could not delete account",
        description: error.message,
        type: "error",
      });
    },
  });
}
