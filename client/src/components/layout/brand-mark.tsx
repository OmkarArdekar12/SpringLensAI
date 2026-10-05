import { SpringLensIcon } from "@/components/icons/springlens-icon";
import { cn } from "@/lib/utils";

export function BrandMark({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "flex items-center gap-2.5 font-semibold tracking-tight",
        className,
      )}
    >
      <SpringLensIcon className="size-8 drop-shadow-[0_0_1px_rgba(0,255,0,0.5)]" />
      <span className="font-heading text-[1.05rem] leading-none">
        SpringLens AI
      </span>
    </div>
  );
}
