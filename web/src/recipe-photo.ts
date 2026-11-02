/** Empty canonical values mean the photo was explicitly removed. Never show phone-local paths on web. */
export function recipePhoto(recipe: Record<string, unknown> | null | undefined): string {
  const value = recipe?.photoDataUrl ?? recipe?.photoUri ?? ''
  return typeof value === 'string' && (/^data:image\/(jpeg|png|webp);base64,/.test(value) || /^https:\/\//.test(value)) ? value : ''
}
