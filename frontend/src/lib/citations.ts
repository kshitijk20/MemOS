import type { Citation } from '@/api/types'

/** Fragment used for in-answer source links; the markdown renderer turns these into buttons. */
export const SOURCE_LINK_PREFIX = '#source-'

/** The [n] a citation has in its answer. Older answers didn't store it; their list order matched. */
export function sourceNumberOf(citation: Citation, index: number): number {
  return citation.sourceNumber ?? index + 1
}

/**
 * Rewrites "[2]" and "[1, 3]" in an answer into markdown links to {@link SOURCE_LINK_PREFIX}n, so
 * each reference can open its source. Leaves real markdown links ("[1](url)") and code alone.
 */
export function linkSourceReferences(markdown: string, availableNumbers: Set<number>): string {
  if (availableNumbers.size === 0) return markdown
  return markdown
    .split(/(```[\s\S]*?```|`[^`\n]*`)/g)
    .map((part, i) =>
      i % 2 === 1
        ? part
        : part.replace(/\[(\d{1,3}(?:\s*,\s*\d{1,3})*)\](?!\()/g, (match, group: string) => {
            const numbers = group.split(',').map((n) => Number(n.trim()))
            if (!numbers.every((n) => availableNumbers.has(n))) return match
            return numbers.map((n) => `[${n}](${SOURCE_LINK_PREFIX}${n})`).join('')
          }),
    )
    .join('')
}
