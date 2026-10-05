"use client";

import { useState } from "react";
import { Trash2, TriangleAlert } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { useCurrentUser, useDeleteAccount } from "@/hooks/use-auth";

/**
 * "Danger zone": permanently deletes the account.
 * The user must type their GitHub username before the final button becomes active.
 */
export function DeleteAccountCard() {
  const { data: user } = useCurrentUser();
  const deleteAccount = useDeleteAccount();
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState("");

  const username = user?.githubUsername ?? "";
  const matches =
    username !== "" && typed.trim().toLowerCase() === username.toLowerCase();

  function handleOpenChange(next: boolean) {
    // Don't allow closing the dialog while the deletion request is running
    if (deleteAccount.isPending) return;
    setOpen(next);
    if (!next) setTyped("");
  }

  return (
    <>
      <Card className="ring-destructive/30">
        <CardHeader>
          <CardTitle className="text-destructive">Danger zone</CardTitle>
          <CardDescription>
            Permanently delete your SpringLens AI account. This cannot be
            undone.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li>Your profile and encrypted GitHub token are erased.</li>
            <li>
              All indexed repositories and their search vectors are erased.
            </li>
            <li>All chat sessions and messages are erased.</li>
            <li>
              You are signed out everywhere, and SpringLens AI is removed from
              your GitHub authorized apps.
            </li>
            <li>Your repositories on GitHub are never touched.</li>
          </ul>
          <Button variant="destructive" onClick={() => setOpen(true)}>
            <Trash2 data-icon="inline-start" />
            Delete my account
          </Button>
        </CardContent>
      </Card>

      <AlertDialog open={open} onOpenChange={handleOpenChange}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle className="flex items-center gap-2">
              <TriangleAlert className="size-5 text-destructive" />
              Delete account permanently?
            </AlertDialogTitle>
            <AlertDialogDescription>
              All your repositories, vectors and chats will be deleted for good.
              There is no way to recover them.
            </AlertDialogDescription>
          </AlertDialogHeader>

          <div className="space-y-2">
            <Label htmlFor="confirm-username">
              Type <span className="font-semibold">{username}</span> to confirm
            </Label>
            <Input
              id="confirm-username"
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              autoComplete="off"
              autoCapitalize="off"
              spellCheck={false}
              disabled={deleteAccount.isPending}
              placeholder={username}
            />
          </div>

          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteAccount.isPending}>
              Cancel
            </AlertDialogCancel>
            <Button
              variant="destructive"
              disabled={!matches || deleteAccount.isPending}
              onClick={() => deleteAccount.mutate(typed.trim())}
            >
              {deleteAccount.isPending ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <Trash2 data-icon="inline-start" />
              )}
              Delete forever
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
