/**
 * Counted labels that read correctly at one.
 *
 * <p>"1 Tasks", "1 Classes" and "1 grades configured" all shipped, in three
 * different files, because each one was written as `${n} Thing` + 's'. One
 * helper so the next counted label does not make it four.
 *
 * The irregular plural is passed in rather than guessed: English has no rule
 * that turns "class" into "classes" and "task" into "tasks" from the singular
 * alone, and a helper that appends "s" would produce "1 classs".
 */
export function plural(count: number, singular: string, pluralForm?: string): string {
  const word = count === 1 ? singular : (pluralForm ?? `${singular}s`);
  return `${count} ${word}`;
}

/**
 * The same, with the count spelled "No" at zero.
 *
 * <p>"0 tasks" is correct and reads like a placeholder that failed to load;
 * "No tasks" reads like an answer.
 */
export function countOrNone(count: number, singular: string, pluralForm?: string): string {
  return count === 0 ? `No ${pluralForm ?? `${singular}s`}` : plural(count, singular, pluralForm);
}
