import { ExternalLink } from "lucide-react";

import { Badge } from "@/components/shadcn-ui/badge";

export function ChatPreview() {
  return (
    <figure
      aria-label="Example conversation: a question about authentication answered with file citations"
      className="w-full max-w-lg overflow-hidden rounded-2xl border bg-card shadow-xl shadow-foreground/5"
    >
      <div className="flex items-center gap-2 border-b bg-muted/40 px-4 py-2.5 text-xs text-muted-foreground">
        <span className="size-2.5 rounded-full bg-primary" />
        acme/payments-api
        <span className="ml-auto rounded-full border px-2 py-0.5">Ready</span>
      </div>

      <div className="space-y-4 p-4 text-sm">
        <div className="ml-auto w-fit max-w-[85%] rounded-2xl rounded-br-md bg-primary px-4 py-2.5 text-primary-foreground">
          Where is the login session created, and how long does it last?
        </div>

        <div className="max-w-[92%] space-y-3 rounded-2xl rounded-bl-md bg-muted px-4 py-3 leading-relaxed">
          <p>
            Sessions are created after GitHub OAuth succeeds in{" "}
            <code className="rounded bg-foreground/10 px-1 py-0.5 text-[0.85em]">
              SecurityConfig
            </code>
            , and the cookie is set to expire after 7 days.
          </p>
          <div className="flex flex-wrap gap-1.5">
            {[
              "config/SecurityConfig.java:41",
              "resources/application.properties:58",
            ].map((file) => (
              <Badge key={file} variant="outline" className="gap-1 font-normal">
                {file}
                <ExternalLink className="size-3 opacity-60" />
              </Badge>
            ))}
          </div>
        </div>
      </div>
      <figcaption className="sr-only">
        SpringLens AI answers questions using only your code, and links every
        claim to the file and line it came from.
      </figcaption>
    </figure>
  );
}
