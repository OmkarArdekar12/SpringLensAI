import type { SVGProps } from "react";

import { cn } from "@/lib/utils";

type SpringLensIconProps = SVGProps<SVGSVGElement> & {
  variant?: "color" | "mono";
};

/**
 * PLACEHOLDER brand mark: a magnifying lens with code brackets inside.
 * Replace the paths below (or swap the component for an <Image>) with your real logo.
 */
export function SpringLensIcon({
  className,
  variant = "color",
  ...props
}: SpringLensIconProps) {
  const mono = variant === "mono";
  const fg = mono ? "var(--background)" : "#FFFFFF";

  return (
    <svg
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={cn("shrink-0", className)}
      {...props}
    >
      <rect width="64" height="64" rx="15" fill={mono ? "currentColor" : "#16A34A"} />
      <circle cx="29" cy="29" r="13" stroke={fg} strokeWidth="4" />
      <path d="M39 39l11 11" stroke={fg} strokeWidth="4.5" strokeLinecap="round" />
      <path
        d="M26 25.5L22 29l4 3.5M32 25.5l4 3.5-4 3.5"
        stroke={fg}
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
