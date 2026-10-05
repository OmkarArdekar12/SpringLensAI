import Image from "next/image";

type SpringLensIconProps = {
  className?: string;
};

export function SpringLensIcon({ className }: SpringLensIconProps) {
  return (
    <Image
      src="/springLensAI.png"
      alt="SpringLensAI"
      width={64}
      height={64}
      className={className}
    />
  );
}
