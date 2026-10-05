import Link from "next/link";
import {
  ArrowRight,
  FolderGit2,
  MessageSquareCode,
  Sparkles,
} from "lucide-react";

import { BackendWarmup } from "@/components/marketing/backend-warmup";
import { ChatPreview } from "@/components/marketing/chat-preview";
import { BrandMark } from "@/components/layout/brand-mark";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/shadcn-ui/accordion";
import { buttonVariants } from "@/components/shadcn-ui/button";
import { ModeToggle } from "@/components/shadcn-ui/mode-toggle";
import { getGithubLoginUrl } from "@/lib/api";
import { siteConfig } from "@/lib/site";
import { cn } from "@/lib/utils";

const steps = [
  {
    title: "Connect GitHub",
    body: "Sign in with GitHub. Public and private repositories you can access appear in your workspace.",
    icon: FolderGit2,
  },
  {
    title: "Index a repository",
    body: "SpringLens AI reads your source files, splits them into chunks and stores their embeddings in Postgres with pgvector.",
    icon: Sparkles,
  },
  {
    title: "Ask in plain English",
    body: "Every answer is built only from your code and links to the exact file and line it came from.",
    icon: MessageSquareCode,
  },
];

const faqs = [
  {
    q: "What is SpringLens AI?",
    a: "SpringLens AI is a web app that lets you chat with a GitHub repository. It uses retrieval-augmented generation (RAG): it finds the most relevant parts of your code and asks Gemini to answer using only those parts.",
  },
  {
    q: "Does it work with private repositories?",
    a: "Yes. You sign in with GitHub and grant the repo scope, so repositories you can access, including private ones, can be indexed. Your access token is encrypted before it is stored.",
  },
  {
    q: "Which AI model does it use?",
    a: "Answers are generated with Google Gemini, and code is embedded with Gemini embeddings and searched with pgvector in PostgreSQL.",
  },
  {
    q: "How do I know an answer is right?",
    a: "Each answer lists the files and line ranges it used. Open the citation to read the original code on GitHub. If the indexed code does not contain the answer, the assistant says it is unsure.",
  },
  {
    q: "Which files get indexed?",
    a: "Source code, configuration and documentation files up to 100 KB. Dependencies, build output, lock files, minified bundles and dotfiles such as .env are skipped.",
  },
];

const jsonLd = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "SoftwareApplication",
      name: siteConfig.name,
      description: siteConfig.description,
      url: siteConfig.url,
      applicationCategory: "DeveloperApplication",
      operatingSystem: "Web",
      offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
    },
    {
      "@type": "FAQPage",
      mainEntity: faqs.map((item) => ({
        "@type": "Question",
        name: item.q,
        acceptedAnswer: { "@type": "Answer", text: item.a },
      })),
    },
  ],
};

export default function HomePage() {
  return (
    <div className="relative min-h-svh overflow-x-hidden">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c"),
        }}
      />
      <BackendWarmup />

      <header className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-4">
        <Link href="/" aria-label={`${siteConfig.name} home`}>
          <BrandMark />
        </Link>
        <nav aria-label="Main" className="flex items-center gap-2">
          <a
            href="#how-it-works"
            className={cn(
              buttonVariants({ variant: "ghost", size: "sm" }),
              "hidden sm:inline-flex",
            )}
          >
            How it works
          </a>
          <a
            href="#faq"
            className={cn(
              buttonVariants({ variant: "ghost", size: "sm" }),
              "hidden sm:inline-flex",
            )}
          >
            FAQ
          </a>
          <ModeToggle />
          <Link href="/login" className={cn(buttonVariants({ size: "sm" }))}>
            Sign in
          </Link>
        </nav>
      </header>

      <main>
        <section className="mx-auto grid w-full max-w-6xl items-center gap-12 px-4 py-14 md:py-24 lg:grid-cols-2">
          <div className="space-y-6">
            <h1 className="font-heading text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
              Ask your codebase anything
            </h1>
            <p className="max-w-prose text-lg text-muted-foreground text-balance">
              Connect GitHub, index a repository, and get answers that point to
              the exact file and line. Built for onboarding, reviews and
              untangling code nobody remembers writing.
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <a
                href={getGithubLoginUrl()}
                className={cn(buttonVariants({ size: "lg" }), "gap-2")}
              >
                <FolderGit2 className="size-4" />
                Continue with GitHub
                <ArrowRight className="size-4" />
              </a>
              <a
                href="#how-it-works"
                className={cn(
                  buttonVariants({ variant: "outline", size: "lg" }),
                )}
              >
                See how it works
              </a>
            </div>
          </div>
          <div className="flex justify-center lg:justify-end">
            <ChatPreview />
          </div>
        </section>

        <section id="how-it-works" className="border-y bg-muted/30">
          <div className="mx-auto w-full max-w-6xl px-4 py-16">
            <h2 className="font-heading text-2xl font-semibold tracking-tight sm:text-3xl">
              From repository to answers in three steps
            </h2>
            <ol className="mt-10 grid gap-8 md:grid-cols-3">
              {steps.map((step, index) => (
                <li key={step.title} className="space-y-3">
                  <div className="flex items-center gap-3">
                    <span className="flex size-10 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">
                      {index + 1}
                    </span>
                    <step.icon
                      className="size-5 text-muted-foreground"
                      aria-hidden="true"
                    />
                  </div>
                  <h3 className="font-heading text-lg font-medium">
                    {step.title}
                  </h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    {step.body}
                  </p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="faq" className="mx-auto w-full max-w-3xl px-4 py-16">
          <h2 className="font-heading text-2xl font-semibold tracking-tight sm:text-3xl">
            Frequently asked questions
          </h2>
          <Accordion className="mt-8">
            {faqs.map((item) => (
              <AccordionItem key={item.q} value={item.q}>
                <AccordionTrigger>{item.q}</AccordionTrigger>
                <AccordionContent>{item.a}</AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        </section>
      </main>

      <footer className="border-t">
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-2 px-4 py-6 text-sm text-muted-foreground sm:flex-row">
          <p>
            &copy; {new Date().getFullYear()} {siteConfig.name}
          </p>
          <p>
            Next.js &mid; Spring Boot &mid; Google Gemini &mid; PostgreSQL &mid;
            pgvector
          </p>
        </div>
      </footer>
    </div>
  );
}
