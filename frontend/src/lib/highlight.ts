const STOP_WORDS = new Set([
  'the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'any', 'can', 'had', 'her', 'was', 'one', 'our',
  'out', 'has', 'have', 'him', 'his', 'how', 'its', 'may', 'new', 'now', 'own', 'say', 'she', 'too', 'use',
  'that', 'this', 'with', 'from', 'they', 'will', 'would', 'there', 'their', 'what', 'about', 'which', 'when',
  'make', 'like', 'into', 'than', 'then', 'them', 'these', 'some', 'could', 'other', 'were', 'been', 'also',
  'more', 'such', 'only', 'over', 'very', 'just', 'your', 'each', 'does', 'did', 'should', 'being', 'where',
])

function contentWords(text: string): string[] {
  return (text.toLowerCase().match(/[\p{L}\p{N}][\p{L}\p{N}_-]*/gu) ?? []).filter(
    (word) => word.length >= 3 && !STOP_WORDS.has(word),
  )
}

/** Sentences (and the whitespace after them), so joining the pieces reproduces the text exactly. */
function splitSentences(text: string): string[] {
  return text.match(/[^.!?\n]+(?:[.!?]+|\n+|$)\s*|\n+/g) ?? [text]
}

export interface HighlightSegment {
  text: string
  highlight: boolean
}

/**
 * Splits a cited passage into sentences and marks the ones the answer most likely drew on, by how
 * many of a sentence's content words also appear in the answer. If nothing clears the bar, the
 * single best-matching sentence is marked so the reader still has a starting point.
 */
export function highlightPassage(passage: string, answer: string): HighlightSegment[] {
  const answerWords = new Set(contentWords(answer.replace(/\[\d+(?:\s*,\s*\d+)*\]/g, ' ')))
  const sentences = splitSentences(passage)

  const scored = sentences.map((sentence) => {
    const words = [...new Set(contentWords(sentence))]
    const matched = words.filter((word) => answerWords.has(word)).length
    return { sentence, matched, ratio: words.length === 0 ? 0 : matched / words.length }
  })

  let marked = scored.map((s) => s.matched >= 3 && s.ratio >= 0.4)
  if (!marked.some(Boolean)) {
    let best = -1
    scored.forEach((s, i) => {
      if (s.matched >= 2 && (best < 0 || s.ratio > scored[best].ratio)) best = i
    })
    marked = scored.map((_, i) => i === best)
  }

  return scored.map((s, i) => ({ text: s.sentence, highlight: marked[i] }))
}
