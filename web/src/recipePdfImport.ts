import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

GlobalWorkerOptions.workerSrc = workerUrl

export type ImportedRecipe = {
  name: string
  ingredients: Array<{ name: string; quantity: number; unit: string; cost: number }>
  instructions: string
}

const unitMap: Record<string, string> = {
  g: 'g', gram: 'g', grams: 'g', kg: 'kg', ml: 'ml', l: 'L', litre: 'L', liter: 'L',
  tsp: 'tsp', teaspoon: 'tsp', tbsp: 'tbsp', tablespoon: 'tbsp',
  cup: 'unit', cups: 'unit', egg: 'unit', eggs: 'unit', pcs: 'unit', pieces: 'unit'
}
const headings = /^(ingredients?|method|directions|instructions?|steps?|preparation|recipe|yield|servings?)\s*:?$/i
const ingredientHeading = /^ingredients?\s*:?$/i
const methodHeading = /^(method|directions|instructions?|steps?|preparation)\s*:?$/i

export async function extractRecipePdf(file: File): Promise<ImportedRecipe> {
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) throw new Error('Please select a PDF file.')
  if (file.size > 10 * 1024 * 1024) throw new Error('Please select a PDF smaller than 10 MB.')
  const bytes = new Uint8Array(await file.arrayBuffer())
  const pdf = await getDocument({ data: bytes }).promise
  if (pdf.numPages > 30) throw new Error('Please use a recipe PDF with no more than 30 pages.')
  const lines: string[] = []
  for (let index = 1; index <= pdf.numPages; index++) {
    const page = await pdf.getPage(index)
    const content = await page.getTextContent()
    let previousY: number | undefined
    let line = ''
    for (const item of content.items) {
      if (!('str' in item)) continue
      const y = 'transform' in item ? item.transform[5] : undefined
      if (y !== undefined && previousY !== undefined && Math.abs(y - previousY) > 3 && line.trim()) {
        lines.push(line.trim()); line = ''
      }
      line += (line && !line.endsWith(' ') ? ' ' : '') + item.str
      previousY = y
      if ('hasEOL' in item && item.hasEOL) { if (line.trim()) lines.push(line.trim()); line = '' }
    }
    if (line.trim()) lines.push(line.trim())
  }
  const clean = lines.map(s => s.replace(/\s+/g, ' ').trim()).filter(Boolean)
  if (!clean.length) throw new Error('No selectable text found. Scanned/image-only PDFs need OCR and cannot be imported yet.')
  const title = clean.find(s => !headings.test(s) && s.length >= 3 && s.length <= 90) || file.name.replace(/\.pdf$/i, '')
  let section: 'other' | 'ingredients' | 'method' = 'other'
  const ingredients: ImportedRecipe['ingredients'] = []
  const instructions: string[] = []
  const pattern = /^(?:[-•*]\s*)?(?:(\d+(?:[.,]\d+)?|\d+\/\d+)\s*)(kg|grams?|g|ml|l|litres?|liters?|tsp|teaspoons?|tbsp|tablespoons?|cups?|eggs?|pieces?|pcs)?\s+(?:of\s+)?(.+)$/i
  for (const line of clean) {
    if (ingredientHeading.test(line)) { section = 'ingredients'; continue }
    if (methodHeading.test(line)) { section = 'method'; continue }
    if (section === 'method') { instructions.push(line); continue }
    if (section !== 'ingredients') continue
    const match = line.match(pattern)
    if (!match) continue
    const raw = match[1]; const quantity = raw.includes('/') ? Number(raw.split('/')[0]) / Number(raw.split('/')[1]) : Number(raw.replace(',', '.'))
    if (!Number.isFinite(quantity) || quantity <= 0) continue
    const unit = unitMap[(match[2] || '').toLowerCase()] || 'unit'
    ingredients.push({ name: match[3].trim(), quantity, unit, cost: 0 })
  }
  if (!ingredients.length) throw new Error('Text was found, but ingredients could not be recognised. Try a PDF with an Ingredients heading and quantities at the start of each line.')
  return { name: title, ingredients, instructions: instructions.join('\n') }
}
