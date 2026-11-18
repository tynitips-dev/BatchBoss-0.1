import { jsPDF } from 'jspdf'

type RecordData = Record<string, unknown>
const text = (value: unknown) => String(value ?? '').trim()
const amount = (value: unknown) => {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? n : 0
}
const money = (value: unknown) => 'R ' + amount(value).toFixed(2)
const safeFile = (value: unknown) => text(value).replace(/[^a-zA-Z0-9_-]+/g, '-').slice(0, 100) || 'invoice'

export function invoicePdfName(invoice: RecordData) {
  return safeFile(invoice.invoiceNumber || invoice.name || invoice.id) + '.pdf'
}

/** Export the saved invoice, not the dashboard or a recalculated estimate. */
export function generateInvoicePdf(invoice: RecordData, business: RecordData) {
  const pdf = new jsPDF({orientation: 'portrait', unit: 'mm', format: 'a4', compress: true})
  const left = 16, right = 194, bottom = 278
  const ref = text(invoice.invoiceNumber || invoice.name || invoice.id)
  const tax = amount(invoice.vat ?? invoice.taxAmount)
  const title = tax > 0 || text(business.vatNumber) ? 'TAX INVOICE' : 'INVOICE'
  let y = 20
  pdf.setProperties({title: title + ' ' + ref, author: text(business.name || business.businessName || 'Bakery')})
  function header(continued: boolean) {
    pdf.setFillColor(194, 41, 100); pdf.rect(0, 0, 210, 3, 'F')
    pdf.setTextColor(32, 42, 56); pdf.setFont('helvetica', 'bold'); pdf.setFontSize(17)
    pdf.text(title, left, 15)
    pdf.setFont('helvetica', 'normal'); pdf.setFontSize(10)
    const referenceLines = pdf.splitTextToSize(ref + (continued ? ' (continued)' : ''), 93) as string[]
    pdf.text(referenceLines, right, 15, {align:'right'})
    y = Math.max(24, 19 + referenceLines.length * 4.7)
  }
  function ensure(height: number) {
    if (y + height > bottom) { pdf.addPage(); header(true) }
  }
  function paragraph(value: unknown, bold = false, width = right-left, x = left) {
    const valueText = text(value)
    if (!valueText) return
    pdf.setFont('helvetica', bold ? 'bold' : 'normal'); pdf.setFontSize(10)
    const lines = pdf.splitTextToSize(valueText, width) as string[]
    for (const line of lines) {
      ensure(5); pdf.setFont('helvetica', bold ? 'bold' : 'normal'); pdf.setFontSize(10)
      pdf.text(line, x, y); y += 5
    }
  }
  function totalRow(label: string, value: unknown, bold = false) {
    ensure(7)
    pdf.setFont('helvetica', bold ? 'bold' : 'normal'); pdf.setFontSize(bold ? 12 : 10)
    pdf.text(label, left, y); pdf.text(money(value), right, y, {align:'right'})
    y += 7
  }
  header(false)
  // Only allow the stored, locally uploaded image formats. A missing/bad logo
  // must not prevent a customer from obtaining the invoice.
  const logo = text(business.logoDataUrl)
  if (logo.length <= 500000 && /^data:image\/(png|jpeg|webp);base64,/i.test(logo)) {
    try {
      const p = pdf.getImageProperties(logo)
      const scale = Math.min(33 / p.width, 23 / p.height)
      pdf.addImage(logo, p.fileType, left, y, p.width * scale, p.height * scale)
      y += 27
    } catch { /* continue without an unreadable logo */ }
  }
  paragraph(business.name || business.businessName || 'Your Bakery', true)
  paragraph(business.address); paragraph(business.phone); paragraph(business.email)
  if (business.vatNumber) paragraph('VAT / Registration: ' + text(business.vatNumber))
  y += 4
  paragraph('BILL TO', true); paragraph(invoice.clientName || 'Client')
  paragraph(invoice.phone || invoice.clientPhone); paragraph(invoice.clientEmail); paragraph(invoice.clientAddress)
  if (invoice.issueDate) paragraph('Issued: ' + text(invoice.issueDate))
  if (invoice.dueDate) paragraph('Due: ' + text(invoice.dueDate))
  if (invoice.status) paragraph('Status: ' + text(invoice.status))
  y += 5; ensure(13)
  pdf.setFillColor(250, 237, 243); pdf.rect(left, y-5, right-left, 8, 'F')
  pdf.setFont('helvetica', 'bold'); pdf.setFontSize(10)
  pdf.text('Item / quantity', left+2, y); pdf.text('Amount', right-2, y, {align:'right'}); y += 9
  const items = Array.isArray(invoice.items) ? invoice.items as RecordData[] : []
  if (!items.length) paragraph(invoice.summary || invoice.orderDescription || invoice.description || 'Saved invoice')
  for (const row of items) {
    const quantity = row.quantity ?? 1
    const label = text(row.name || row.description || 'Item') + '  x ' + text(quantity) + (row.unit ? ' ' + text(row.unit) : '')
    pdf.setFont('helvetica', 'normal'); pdf.setFontSize(10)
    const lines = pdf.splitTextToSize(label, 122) as string[]
    ensure(6)
    pdf.setFont('helvetica', 'normal'); pdf.setFontSize(10)
    pdf.text(money(row.lineTotal ?? amount(quantity) * amount(row.unitPrice)), right-2, y, {align:'right'})
    for (const line of lines) { ensure(5); pdf.setFont('helvetica', 'normal'); pdf.setFontSize(10); pdf.text(line, left+2, y); y += 5 }
    y += 2
  }
  y += 3; ensure(35)
  if (invoice.subtotal != null) totalRow('Subtotal', invoice.subtotal)
  if (amount(invoice.discount ?? invoice.discountAmount)) totalRow('Discount', invoice.discount ?? invoice.discountAmount)
  if (tax) totalRow('Tax / VAT', tax)
  totalRow('TOTAL DUE', invoice.total ?? invoice.amount, true)
  y += 5
  if (invoice.notes) { paragraph('NOTES', true); paragraph(invoice.notes); y += 3 }
  paragraph('PAYMENT DETAILS', true); paragraph(business.bankName)
  if (business.accountNumber) paragraph('Account: ' + text(business.accountNumber))
  if (business.branchCode) paragraph('Branch: ' + text(business.branchCode))
  paragraph('Reference: ' + ref)
  const pages = pdf.getNumberOfPages()
  for (let p = 1; p <= pages; p++) {
    pdf.setPage(p); pdf.setFontSize(9); pdf.setTextColor(95, 103, 115)
    pdf.text('Page ' + p + ' of ' + pages, right, 289, {align:'right'})
  }
  return pdf
}

export function downloadInvoicePdf(invoice: RecordData, business: RecordData) {
  generateInvoicePdf(invoice, business).save(invoicePdfName(invoice))
}

/** Open the actual PDF for printing in the device PDF viewer, not window.print on the dashboard. */
export function openInvoicePdf(invoice: RecordData, business: RecordData): boolean {
  const blob = generateInvoicePdf(invoice, business).output('blob')
  const url = URL.createObjectURL(blob)
  const tab = window.open(url, '_blank')
  if (!tab) { URL.revokeObjectURL(url); downloadInvoicePdf(invoice, business); return false }
  try { tab.opener = null } catch { /* browser may restrict the viewer */ }
  window.setTimeout(() => URL.revokeObjectURL(url), 300000)
  return true
}

export async function shareInvoicePdf(invoice: RecordData, business: RecordData): Promise<'shared'|'downloaded'|'cancelled'> {
  const pdf = generateInvoicePdf(invoice, business)
  const file = new File([pdf.output('blob')], invoicePdfName(invoice), {type:'application/pdf'})
  const payload = {files:[file], title:'Invoice ' + text(invoice.invoiceNumber || invoice.name)}
  if (typeof navigator.share === 'function' && typeof navigator.canShare === 'function' && navigator.canShare(payload)) {
    try { await navigator.share(payload); return 'shared' }
    catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled'
    }
  }
  pdf.save(invoicePdfName(invoice))
  return 'downloaded'
}
