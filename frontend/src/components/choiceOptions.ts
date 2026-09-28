import type { QuestionContentConfig } from '@/types/domain'

export function optionLabel(index: number): string {
  let value = index + 1, label = ''
  while (value > 0) { value--; label = String.fromCharCode(65 + value % 26) + label; value = Math.floor(value / 26) }
  return label
}
export function newChoice() { return { id: `opt_${crypto.randomUUID()}`, label: '' } }
export function defaultChoices() { return Array.from({ length: 4 }, newChoice) }
export function choiceText(config: QuestionContentConfig | undefined, id: string, index: number) {
  const content = config?.choices?.find(option => option.id === id)?.label
  return `${optionLabel(index)}. ${content ?? (config?.choices ? '' : id)}`
}
