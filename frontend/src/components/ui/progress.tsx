import * as React from "react"
import { Progress as ProgressPrimitive } from "radix-ui"

import { cn } from "@/lib/utils"

function Progress({
  className,
  value,
  indeterminate,
  ...props
}: React.ComponentProps<typeof ProgressPrimitive.Root> & { indeterminate?: boolean }) {
  return (
    <ProgressPrimitive.Root
      data-slot="progress"
      className={cn(
        "relative flex h-1 w-full items-center overflow-x-hidden rounded-full bg-muted",
        className
      )}
      {...props}
    >
      {indeterminate ? (
        <div className="absolute inset-y-0 w-1/3 rounded-full bg-primary animate-[indeterminate_1.4s_ease-in-out_infinite]" />
      ) : (
        <ProgressPrimitive.Indicator
          data-slot="progress-indicator"
          className="size-full flex-1 bg-primary transition-all duration-300"
          style={{ transform: `translateX(-${100 - (value || 0)}%)` }}
        />
      )}
    </ProgressPrimitive.Root>
  )
}

export { Progress }
