import { useRef, useState, type KeyboardEvent } from 'react'
import { Send } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

const MAX_LENGTH = 8000

interface ChatInputProps {
  onSend: (content: string) => void
  disabled?: boolean
}

export function ChatInput({ onSend, disabled }: ChatInputProps) {
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  function handleSend() {
    const trimmed = value.trim()
    if (trimmed === '' || disabled) return
    onSend(trimmed)
    setValue('')
    // On phones, close the keyboard so the answer has room. Done explicitly rather than as a side
    // effect of disabling the field, which leaves iOS Safari's page offset behind.
    if (window.matchMedia('(pointer: coarse)').matches) {
      textareaRef.current?.blur()
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="flex items-end gap-2">
      <Textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="Ask a question… (Enter to send, Shift+Enter for a new line)"
        maxLength={MAX_LENGTH}
        disabled={disabled}
        className="max-h-48"
      />
      <Button onClick={handleSend} disabled={disabled || value.trim() === ''}>
        <Send className="size-4" />
        Send
      </Button>
    </div>
  )
}
