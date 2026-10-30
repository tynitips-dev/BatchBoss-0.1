import { firebaseConfig } from "./config.js";

const root = document.getElementById("app");
const configured = ["apiKey", "authDomain", "projectId", "appId"].every(key => Boolean(firebaseConfig[key]));
const money = cents => "R" + (Number(cents || 0) / 100).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const esc = value => String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
const formatDate = millis => millis ? new Date(millis).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—";

const DEMO = {
  role: "partner",
  partner: { fullName: "Sarah Molefe", email: "sample@example.co.za", status: "approved", referralCode: "BB-DEMO123", province: "Gauteng" },
  leads: [
    { id: "demo1", businessName: "Sweet Treats Bakery", businessEmail: "hello@example.co.za", leadType: "baker", agentStage: "contacted", saleStatus: "payment-verified", createdAtMillis: Date.now() - 86400000 * 2 },
    { id: "demo2", businessName: "Golden Whisk Packaging", businessEmail: "info@example.co.za", leadType: "supplier", agentStage: "follow-up", saleStatus: "unverified", createdAtMillis: Date.now() - 86400000 * 5 },
    { id: "demo3", businessName: "Bloom Cakes", businessEmail: "cakes@example.co.za", leadType: "baker", agentStage: "new", saleStatus: "unverified", createdAtMillis: Date.now() - 86400000 * 6 }
  ],
  commissions: [
    { id: "sample1", businessName: "Sweet Treats Bakery", planId: "bakerAnnual", amountCents: 119900, commissionCents: 29975, rateBps: 2500, status: "approved", createdAtMillis: Date.now() - 86400000 * 2 },
    { id: "sample2", businessName: "Bake Supply Co.", planId: "supplierGrowth", amountCents: 39900, commissionCents: 7980, rateBps: 2000, status: "paid", createdAtMillis: Date.now() - 86400000 * 12 }
  ]
};

const state = {
  user: null, overview: configured ? null : DEMO, admin: null,
  page: "overview", authMode: "signin", error: "", loading: configured, busy: false
};
let firebase = null;
function statusPill(value) {
  const labels = {
    approved: "Approved", pending: "Pending", paid: "Paid", reversed: "Reversed",
    contacted: "Contacted", "follow-up": "Follow-up", new: "New",
    "payment-verified": "Verified sale", unverified: "Not paid",
    suspended: "Suspended", rejected: "Rejected"
  };
  const cls = ["approved", "paid", "payment-verified"].includes(value) ? "green" :
    ["pending", "follow-up"].includes(value) ? "amber" :
    ["rejected", "reversed", "suspended"].includes(value) ? "red" : "pink";
  return '<span class="pill ' + cls + '">' + esc(labels[value] || value) + '</span>';
}
function button(text, action, extra = "") {
  return '<button class="btn ' + extra + '" type="button" data-action="' + action + '">' + text + '</button>';
}
function card(title, inner, subtitle = "") {
  return '<section class="panel"><div class="panel-heading"><div><h2>' + title +
    '</h2>' + (subtitle ? '<p>' + subtitle + '</p>' : "") + '</div></div>' + inner + '</section>';
}
function notice(text, cls = "") { return '<div class="notice ' + cls + '">' + text + '</div>'; }
function brand(dark = false) {
  return '<div class="brand"><span class="brand-mark">B</span><span>Batch<em>Boss</em><small>SALES PARTNERS</small></span></div>';
}
function toast(message) {
  const item = document.createElement("div");
  item.className = "toast"; item.textContent = message; document.body.append(item);
  setTimeout(() => item.remove(), 3500);
}
function showError(message) { state.error = message; render(); }
function errorPanel() { return state.error ? '<div class="error" role="alert">' + esc(state.error) + '</div>' : ""; }
async function call(name, data = {}) {
  if (!firebase) throw new Error("Firebase is not connected. This preview cannot save records.");
  const fn = firebase.httpsCallable(firebase.functions, name);
  const result = await fn(data);
  return result.data;
}
async function refresh() {
  if (!state.user) { state.loading = false; render(); return; }
  state.loading = true; state.error = ""; render();
  try {
    if (!state.user.emailVerified) { state.overview = null; state.loading = false; render(); return; }
    state.overview = await call("getPartnerOverview");
    if (state.overview?.role === "admin") await loadAdmin();
  } catch (error) { state.error = error.message || "Unable to load your workspace."; }
  state.loading = false; render();
}
async function loadAdmin() {
  const [partners, leads, commissions] = await Promise.all([
    call("adminListPartners"), call("adminListLeads"), call("adminListCommissions")
  ]);
  state.admin = { partners: partners.partners, leads: leads.leads, commissions: commissions.commissions,
    truncated: Boolean(partners.truncated || leads.truncated || commissions.truncated) };
}
function renderAuth() {
  const signup = state.authMode === "register";
  return '<div class="auth-frame"><div class="portal-hero"><img src="./batchboss-partners-hero.png" width="1983" height="232" alt="BatchBoss Partners Portal — Let’s create greater possibilities. Collaborate, support bakers and grow together." fetchpriority="high" decoding="async"></div><section class="auth-card">' + brand() +
    '<div class="eyebrow">Your opportunity starts here</div>' +
    '<h1>' + (signup ? "Apply to become a partner" : "Welcome back") + '</h1>' +
    '<p class="subtext">' + (signup ? "Create an account, verify your email and submit an application." :
      "Sign in to manage your referrals, leads and commissions.") + '</p>' +
    '<div class="auth-switch">' + button("Sign in", "signin-mode", signup ? "light" : "") +
    button("Apply", "register-mode", signup ? "" : "light") + '</div>' + errorPanel() +
    '<form class="auth-form" data-form="' + (signup ? "register" : "signin") + '">' +
    '<div class="field"><label for="auth-email">Email address</label><input id="auth-email" name="email" required type="email" autocomplete="email" placeholder="you@example.co.za"></div>' +
    '<div class="field"><label for="auth-password">Password</label><input id="auth-password" name="password" required minlength="8" type="password" autocomplete="' +
      (signup ? "new-password" : "current-password") + '" placeholder="At least 8 characters"></div>' +
    '<button class="btn pink" type="submit" ' + (state.busy ? "disabled" : "") + '>' +
      (signup ? "Create my account" : "Sign in") + '</button></form>' +
    '<p class="subtext" style="margin-top:18px">Applications require approval. Commission rates and any engagement terms will be finalised before the programme launches.</p></section>' +
    '<section class="auth-feature"><div><div class="eyebrow" style="color:#ff95b7">BATCHBOSS PARTNERS</div><h2>Help bakers grow.<br>Build your own opportunities.</h2>' +
    '<p>Introduce home bakers, commercial bakeries and suppliers to the tools and advertising opportunities at BatchBoss.</p></div>' +
    '<div class="auth-highlight">✦ Your own referral code<br>✦ Track businesses and follow-ups<br>✦ Transparent verified-sales commissions<br>✦ Dedicated support from BatchBoss</div>' +
    '<small>Independent partner programme in development · South Africa</small></section></div>';
}
function renderApplication() {
  const o = state.overview || {};
  if (!state.user?.emailVerified) {
    return '<div class="auth-frame"><div class="portal-hero"><img src="./batchboss-partners-hero.png" width="1983" height="232" alt="BatchBoss Partners Portal — Let’s create greater possibilities. Collaborate, support bakers and grow together." fetchpriority="high" decoding="async"></div><section class="auth-card">' + brand() +
      '<h1>Verify your email</h1><p class="subtext">Check your inbox for the Firebase verification link. You must verify your email before applying.</p>' +
      errorPanel() + '<div class="actions">' + button("I have verified my email", "verify-email") +
      button("Resend verification email", "resend-verification", "light") + button("Sign out", "logout", "light") + '</div></section></div>';
  }
  const applied = o.status && o.status !== "not-applied";
  return '<div class="auth-frame"><div class="portal-hero"><img src="./batchboss-partners-hero.png" width="1983" height="232" alt="BatchBoss Partners Portal — Let’s create greater possibilities. Collaborate, support bakers and grow together." fetchpriority="high" decoding="async"></div><section class="auth-card">' + brand() +
    '<div class="eyebrow">Partner onboarding</div><h1>' + (applied ? "Application: " + esc(o.status) : "Tell us about yourself") + '</h1>' +
    '<p class="subtext">' + (applied ?
      "Your application is being reviewed by BatchBoss. Approval is required before you can record leads." :
      "Complete your application. We will review it before enabling your partner workspace.") + '</p>' +
    errorPanel() + (applied ? notice("Your account is not yet approved. Contact info@batchboss.co.za if you need help.", "blue") +
      button("Refresh status", "refresh") : '<form class="auth-form" data-form="apply" style="margin-top:25px">' +
      '<div class="field"><label>Full name</label><input name="fullName" minlength="3" maxlength="100" autocomplete="name" required></div>' +
      '<div class="field"><label>Province</label><select required name="province">' + provinces() + '</select></div>' +
      '<label class="check"><input type="checkbox" required name="acceptProgrammeReview">I understand this is an application to a proposed programme, not a job offer or a guarantee of income. Final terms are subject to approval.</label>' +
      '<button type="submit" class="btn pink">Submit application</button></form>') +
    '<p style="margin-top:25px">' + button("Sign out", "logout", "light") + '</p></section>' +
    '<section class="auth-feature"><div><h2>Everything you need to grow your network.</h2>' +
    '<p>Approved partners get a private dashboard to follow up with businesses and view commissions after payments have been independently checked.</p></div>' +
    '<div class="auth-highlight">01 · Apply<br>02 · Get approved<br>03 · Introduce businesses<br>04 · Earn on verified qualifying sales</div></section></div>';
}
function provinces() {
  return '<option value="">Choose province</option>' +
    ["Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo", "Mpumalanga", "North West", "Northern Cape", "Western Cape"]
      .map(p => '<option value="' + esc(p) + '">' + esc(p) + '</option>').join("");
}
function shell(content) {
  const isAdmin = state.overview?.role === "admin";
  const nav = isAdmin ? [
    ["agents", "♙", "Applications"], ["sales", "▤", "Verified sales"], ["payouts", "◈", "Commissions"]
  ] : [
    ["overview", "◫", "Overview"], ["leads", "♙", "My leads"],
    ["commissions", "◈", "Commissions"], ["resources", "▣", "Sales resources"]
  ];
  const pageLabel = nav.find(v => v[0] === state.page)?.[2] || "Overview";
  const name = isAdmin ? "Administrator" : (state.overview?.partner?.fullName || "Partner");
  const initials = name.split(/\s+/).slice(0, 2).map(v => v[0]).join("").toUpperCase();
  return '<div class="workspace"><aside class="sidebar">' + brand(true) +
    '<div class="side-caption">PARTNER WORKSPACE</div><nav class="nav" aria-label="Workspace">' +
    nav.map(([id, ico, title]) => '<button data-action="tab" data-tab="' + id +
      '" class="' + (state.page === id ? "active" : "") + '"><span class="nav-symbol">' +
      ico + '</span>' + title + '</button>').join("") + '</nav>' +
    '<div class="sidebar-foot"><strong>Need a little help?</strong><p>Questions about referrals, packages or your account?</p>' +
    '<a href="mailto:info@batchboss.co.za">Contact partner support ↗</a></div></aside>' +
    '<div class="main"><header class="topbar"><span class="topbar-label">Partners / ' + esc(pageLabel) + '</span>' +
    '<div class="topbar-actions">' + (!configured ? '<span class="pill amber">Preview only</span>' : '<span class="pill green">Secure workspace</span>') +
    '<span class="avatar" aria-label="' + esc(name) + '">' + esc(initials) + '</span>' +
    (configured ? button("Sign out", "logout", "light small") : "") + '</div></header>' +
    '<div class="page">' + errorPanel() + content + '</div></div></div>';
}
function stats() {
  const leads = state.overview?.leads || [];
  const commissions = state.overview?.commissions || [];
  const pending = commissions.filter(v => v.status === "pending" || v.status === "approved").reduce((n, v) => n + v.commissionCents, 0);
  const earned = commissions.filter(v => v.status === "paid").reduce((n, v) => n + v.commissionCents, 0);
  const vals = [
    ["Total leads", leads.length.toString(), "Businesses introduced", "♙"],
    ["Verified sales", commissions.filter(v => v.status !== "reversed").length.toString(), "Payment confirmed by admin", "✓"],
    ["Commission paid", money(earned), "Recorded payouts", "◈"],
    ["Awaiting payout", money(pending), "Pending + approved", "◷"]
  ];
  return '<div class="stats">' + vals.map(([title, number, description, icon]) =>
    '<div class="stat"><div class="stat-label"><span>' + title + '</span><span class="stat-icon">' +
    icon + '</span></div><span class="number">' + number + '</span><small>' + description + '</small></div>').join("") + '</div>';
}
function leadTable(items, small = false) {
  if (!items.length) return '<div class="empty"><strong>No leads yet</strong>When you register your first business, it will appear here.</div>';
  return '<div class="table-wrap"><table class="data-table"><thead><tr><th>Business</th><th>Type</th><th>Follow-up</th><th>Sale status</th><th>Added</th></tr></thead><tbody>' +
    items.map(v => '<tr><td><strong>' + esc(v.businessName) + '</strong><small>' +
      esc(v.businessEmail) + '</small></td><td>' + esc(v.leadType) + '</td><td>' +
      statusPill(v.agentStage) + '</td><td>' + statusPill(v.saleStatus) + '</td><td>' +
      formatDate(v.createdAtMillis) + '</td></tr>').join("") + '</tbody></table></div>';
}
function commissionTable(items) {
  if (!items.length) return '<div class="empty"><strong>No verified sales yet</strong>Commissions appear only after the payment is checked and recorded by an administrator.</div>';
  return '<div class="table-wrap"><table class="data-table"><thead><tr><th>Customer</th><th>Plan</th><th>Sale value</th><th>Commission</th><th>Status</th></tr></thead><tbody>' +
    items.map(v => '<tr><td><strong>' + esc(v.businessName) + '</strong><small>' +
      formatDate(v.createdAtMillis) + '</small></td><td>' + esc(planLabel(v.planId)) +
      '</td><td>' + money(v.amountCents) + '</td><td class="amount">' +
      money(v.commissionCents) + '</td><td>' + statusPill(v.status) + '</td></tr>').join("") +
    '</tbody></table></div>';
}
function planLabel(id) {
  return ({ bakerMonthly: "Baker Pro Monthly", bakerAnnual: "Baker Pro Annual",
    supplierDirectory: "Supplier Directory", supplierGrowth: "Supplier Growth",
    supplierFeatured: "Supplier Featured" })[id] || id || "—";
}
function renderOverview() {
  const p = state.overview.partner;
  const code = p.referralCode || "Pending approval";
  const bakerUrl = "https://app.batchboss.co.za/?signup=1&ref=" + encodeURIComponent(code);
  const supplierUrl = "https://suppliers.batchboss.co.za/?ref=" + encodeURIComponent(code);
  const linkDisabled = !configured || !/^BB-[A-F0-9]{12}$/.test(code) ? " disabled" : "";
  const banner = !configured ? notice("Preview data only. This is a visual demonstration, not a live sales or payment system.", "blue") : "";
  return '<div class="heading"><div><div class="eyebrow">Your growth hub</div><h1>Welcome back, ' +
    esc(p.fullName.split(" ")[0]) + ' 👋</h1><p class="subtext">Your businesses, sales and commissions in one place.</p></div>' +
    button("+ Add a lead", "go-leads", "pink") + '</div>' + banner +
    '<div class="portal-hero"><img src="./batchboss-partners-hero.png" width="1983" height="232" alt="BatchBoss Partners Portal — Let’s create greater possibilities. Collaborate, support bakers and grow together." fetchpriority="high" decoding="async"></div>' +
    '<div class="hero"><div><span class="pill pink">BATCHBOSS SALES PARTNERS</span><h2>Every introduction counts.</h2>' +
    '<p>Bring South African bakers and suppliers together with the tools they need. Your dashboard keeps the details organised.</p></div>' +
    '<div class="hero-graphic"><span class="coin">✦</span></div></div>' + stats() +
    '<div class="grid-two"><div>' + card("Recent introductions", leadTable(state.overview.leads.slice(0, 5)), "Businesses you have brought to BatchBoss") +
    '</div><div>' + card("Your referral details",
      '<p class="subtext">Partner code</p><h2 style="font-size:24px">' + esc(code) + '</h2>' +
      '<div class="referral"><small>Baker signup link — not live</small><code>' + esc(bakerUrl) +
      '</code><button data-action="copy-ref" data-kind="baker" type="button" class="btn light small"' + linkDisabled + '>Copy baker link</button></div>' +
      '<div class="referral"><small>Supplier signup link — not live</small><code>' + esc(supplierUrl) +
      '</code><button data-action="copy-ref" data-kind="supplier" type="button" class="btn light small"' + linkDisabled + '>Copy supplier link</button></div>' +
      notice("Referral attribution is tested locally only. These signup links do not earn automatic commission yet. Register your leads here while launch checks are completed.", "blue"),
      "One code for baker and supplier introductions") + '</div></div>';
}
function renderLeads() {
  const o = state.overview;
  return '<div class="heading"><div><div class="eyebrow">Pipeline</div><h1>My business leads</h1><p class="subtext">Keep track of the bakeries and suppliers you introduce.</p></div></div>' +
    (!configured ? notice("Preview only: adding and editing leads requires the live Firebase connection.", "blue") : "") +
    '<div class="grid-two"><div>' + card("Your introductions", leadTable(o.leads)) +
    (o.leads.length ? card("Update a follow-up",
      '<form class="fields" data-form="update-lead"><div class="field wide"><label>Business</label>' +
      '<select name="leadId" required>' + o.leads.map(v => '<option value="' + esc(v.id) + '">' + esc(v.businessName) + '</option>').join("") + '</select></div>' +
      '<div class="field"><label>Follow-up stage</label><select name="agentStage"><option value="new">New</option><option value="contacted">Contacted</option><option value="follow-up">Follow-up needed</option></select></div>' +
      '<div class="field wide"><label>Notes</label><textarea name="note" maxlength="500" placeholder="When will you check in again?"></textarea></div>' +
      '<div class="field wide"><button class="btn" type="submit" ' + (!configured ? "disabled" : "") + '>Save follow-up</button></div></form>') : "") +
    '</div><div>' + card("Add a new business",
      '<form data-form="add-lead" class="fields"><div class="field wide"><label>Business name</label><input name="businessName" required maxlength="120" placeholder="e.g. Fresh Bakes Johannesburg"></div>' +
      '<div class="field wide"><label>Business email</label><input name="businessEmail" type="email" maxlength="254" required placeholder="hello@business.co.za"></div>' +
      '<div class="field"><label>Business type</label><select name="leadType" required><option value="baker">Baker</option><option value="supplier">Supplier</option></select></div>' +
      '<div class="field"><label>Province</label><select name="province" required>' + provinces() + '</select></div>' +
      '<label class="check field wide"><input name="permissionConfirmed" type="checkbox" required>I have permission to record this business contact and share it with BatchBoss for this introduction.</label>' +
      '<div class="field wide"><button class="btn pink" type="submit" ' + (!configured ? "disabled" : "") + '>Save business lead</button></div></form>',
      "Only add business details you are authorised to share") + '</div></div>';
}
function renderCommissions() {
  const items = state.overview.commissions;
  const paid = items.filter(v => v.status === "paid").reduce((sum, v) => sum + v.commissionCents, 0);
  const pending = items.filter(v => v.status === "pending" || v.status === "approved").reduce((sum, v) => sum + v.commissionCents, 0);
  return '<div class="heading"><div><div class="eyebrow">My earnings</div><h1>Commission tracker</h1>' +
    '<p class="subtext">A transparent record of verified qualifying sales.</p></div></div>' +
    '<div class="stats"><div class="stat"><div class="stat-label">Total paid</div><strong class="number">' + money(paid) + '</strong></div>' +
    '<div class="stat"><div class="stat-label">Awaiting payout</div><strong class="number">' + money(pending) + '</strong></div></div>' +
    card("Commission history", commissionTable(items)) +
    notice("Commissions are calculated only from verified first payments. Pending and approved balances are not yet paid. Payouts are recorded by an administrator, not sent automatically.", "blue");
}
function renderResources() {
  const pitch = "Good day! I’m a BatchBoss Sales Partner. BatchBoss helps South African bakers manage recipes, costing, stock, quotes and invoices. Would you like more information about the baker plans or supplier advertising opportunities?";
  return '<div class="heading"><div><div class="eyebrow">Partner toolkit</div><h1>Sales resources</h1>' +
    '<p class="subtext">Simple ways to start conversations with bakers and suppliers.</p></div></div>' +
    card("Your opening message", '<div class="resource"><p>' + esc(pitch) + '</p></div>' +
      '<p style="margin-top:15px">' + button("Copy message", "copy-pitch", "light") + '</p>') +
    '<div class="grid-two"><div>' + card("For bakers", '<div class="resource"><h3>Help them run their baking business</h3><p>Recipe costing, ingredients, stock control, quotes, invoices and reports in one workspace.</p></div>') + '</div>' +
    '<div>' + card("For suppliers", '<div class="resource"><h3>Get discovered by bakers</h3><p>Directory profiles, specials and advertising placements are planned across BatchBoss channels, subject to package availability.</p></div>') + '</div></div>' +
    notice("Published package offers must reflect actual live availability. Do not promise earnings, guarantees, or unapproved promotions.", "blue");
}
function planOptions() {
  return '<option value="">Select package</option>' +
    ["bakerMonthly", "bakerAnnual", "supplierDirectory", "supplierGrowth", "supplierFeatured"]
      .map(v => '<option value="' + v + '">' + esc(planLabel(v)) + '</option>').join("");
}
function renderAdmin() {
  const admin = state.admin || { partners: [], leads: [], commissions: [] };
  const applications = admin.partners;
  const page = state.page;
  let inner = "";
  if (page === "agents") {
    inner = '<div class="heading"><div><div class="eyebrow">Partner operations</div><h1>Sales partner applications</h1>' +
      '<p class="subtext">Review applicants and control access to the sales programme.</p></div></div>' +
      card("Partner directory", applications.length ? '<div class="table-wrap"><table class="data-table"><thead><tr><th>Applicant</th><th>Province</th><th>Status</th><th>Action</th></tr></thead><tbody>' +
        applications.map(v => '<tr><td><strong>' + esc(v.fullName) + '</strong><small>' + esc(v.email) +
          '</small><small>' + esc(v.referralCode || "No referral code yet") + '</small></td><td>' + esc(v.province) +
          '</td><td>' + statusPill(v.status) + '</td><td><div class="actions">' +
          (v.status !== "approved" && v.status !== "rejected" ? '<button class="btn small" data-action="review" data-decision="approved" data-uid="' + esc(v.uid) + '">Approve</button>' : "") +
          (v.status === "pending" ? '<button class="btn small danger" data-action="review" data-decision="rejected" data-uid="' + esc(v.uid) + '">Reject</button>' : "") +
          (v.status === "approved" ? '<button class="btn small danger" data-action="review" data-decision="suspended" data-uid="' + esc(v.uid) + '">Suspend</button>' : "") +
          '</div></td></tr>').join("") + '</tbody></table></div>' :
        '<div class="empty">No partner applications yet.</div>');
  } else if (page === "sales") {
    inner = '<div class="heading"><div><div class="eyebrow">Manual verification</div><h1>Record verified first sales</h1>' +
      '<p class="subtext">Use the merchant dashboard to confirm a payment before making an entry.</p></div></div>' +
      notice("This form does not check Paystack or PayFast directly. The administrator must independently verify the real successful payment, currency and amount. Never enter test transactions.", "red") +
      '<div class="grid-two"><div>' + card("Unpaid / pending leads",
        '<div class="table-wrap"><table class="data-table"><thead><tr><th>Business</th><th>Type</th><th>Status</th></tr></thead><tbody>' +
        admin.leads.map(v => '<tr><td><strong>' + esc(v.businessName) + '</strong><small>' +
          esc(v.businessEmail) + '</small></td><td>' + esc(v.leadType) + '</td><td>' + statusPill(v.saleStatus) +
          '</td></tr>').join("") + '</tbody></table></div>') + '</div><div>' +
        card("Record a payment", '<form class="fields" data-form="verified-sale">' +
          '<div class="field wide"><label>Business lead</label><select name="leadId" required><option value="">Select lead</option>' +
          admin.leads.filter(v => v.saleStatus !== "payment-verified").map(v => '<option value="' + esc(v.id) + '">' +
            esc(v.businessName) + " (" + esc(v.leadType) + ")" + '</option>').join("") + '</select></div>' +
          '<div class="field wide"><label>Package</label><select name="planId" required>' + planOptions() + '</select></div>' +
          '<div class="field"><label>Provider</label><select name="provider"><option value="paystack">Paystack</option><option value="payfast">PayFast</option></select></div>' +
          '<div class="field"><label>Paid amount (R)</label><input type="number" name="amount" min="0.01" max="1000000" step="0.01" required></div>' +
          '<div class="field wide"><label>Provider payment reference</label><input name="reference" minlength="6" maxlength="150" required></div>' +
          '<label class="check field wide"><input type="checkbox" name="merchantPaymentConfirmed" required>I personally checked this successful real payment in the merchant system, including its amount and currency.</label>' +
          '<div class="field wide"><button class="btn pink" type="submit">Record verified sale</button></div></form>',
          "Creates a pending commission, not a payout") + '</div></div>';
  } else {
    inner = '<div class="heading"><div><div class="eyebrow">Finance controls</div><h1>Commission approvals</h1>' +
      '<p class="subtext">Approve verified commissions and record manual payouts with an audit note.</p></div></div>' +
      card("Commission ledger", admin.commissions.length ? '<div class="table-wrap"><table class="data-table"><thead><tr><th>Business</th><th>Package</th><th>Amount</th><th>Commission</th><th>Status</th></tr></thead><tbody>' +
        admin.commissions.map(v => '<tr><td><strong>' + esc(v.businessName) + '</strong><small>' + esc(v.provider) +
          " · " + esc(v.reference) + '</small></td><td>' + esc(planLabel(v.planId)) +
          '</td><td>' + money(v.amountCents) + '</td><td class="amount">' + money(v.commissionCents) +
          '</td><td>' + statusPill(v.status) + '</td></tr>').join("") + '</tbody></table></div>' :
        '<div class="empty">No commissions recorded.</div>') +
      card("Approve, record payout or reverse", '<form class="fields" data-form="commission-change">' +
        '<div class="field wide"><label>Commission</label><select name="commissionId" required><option value="">Choose commission</option>' +
        admin.commissions.filter(v => v.status !== "reversed").map(v => '<option value="' + esc(v.id) +
          '">' + esc(v.businessName) + " · " + money(v.commissionCents) + " · " + esc(v.status) + '</option>').join("") +
        '</select></div><div class="field"><label>New status</label><select name="nextStatus"><option value="approved">Approved</option><option value="paid">Paid (manual payment completed)</option><option value="reversed">Reversed</option></select></div>' +
        '<div class="field wide"><label>Audit note / payment evidence</label><textarea name="note" required maxlength="250" placeholder="Reason or payout reference, verified outside this portal"></textarea></div>' +
        '<div class="field wide"><button class="btn" type="submit">Update commission</button></div></form>') +
      notice("Mark as Paid only after you have made and confirmed a payout outside this portal. Reversing a paid entry does not recover funds automatically.", "blue");
  }
  return (admin.truncated ? notice("More than 200 records exist. Pagination and exports must be added before full operational use.", "blue") : "") + inner;
}
function render() {
  if (state.loading) { root.innerHTML = '<main class="loading-screen">' + brand() + '<p>Loading your BatchBoss workspace…</p></main>'; return; }
  if (configured && !state.user) { root.innerHTML = renderAuth(); return; }
  if (configured && (!state.user.emailVerified || !state.overview || state.overview.role === "applicant")) {
    root.innerHTML = renderApplication(); return;
  }
  const admin = state.overview?.role === "admin";
  if (admin && !["agents", "sales", "payouts"].includes(state.page)) state.page = "agents";
  if (!admin && !["overview", "leads", "commissions", "resources"].includes(state.page)) state.page = "overview";
  const content = admin ? renderAdmin() :
    state.page === "leads" ? renderLeads() :
    state.page === "commissions" ? renderCommissions() :
    state.page === "resources" ? renderResources() : renderOverview();
  root.innerHTML = shell(content);
}
async function perform(action) {
  state.busy = true;
  try { await action(); state.error = ""; } catch (error) { state.error = error.message || "An unexpected error occurred."; }
  state.busy = false; render();
}
root.addEventListener("click", async event => {
  const buttonEl = event.target.closest("[data-action]");
  if (!buttonEl) return;
  const action = buttonEl.dataset.action;
  if (action === "tab") { state.page = buttonEl.dataset.tab; state.error = ""; render(); return; }
  if (action === "go-leads") { state.page = "leads"; render(); return; }
  if (action === "signin-mode" || action === "register-mode") { state.authMode = action === "signin-mode" ? "signin" : "register"; state.error = ""; render(); return; }
  if (action === "logout") { await perform(async () => firebase.authSignOut(firebase.auth)); return; }
  if (action === "refresh") { await refresh(); return; }
  if (action === "verify-email") { await perform(async () => { await state.user.reload(); await state.user.getIdToken(true); await refresh(); }); return; }
  if (action === "resend-verification") { await perform(async () => { await firebase.sendEmailVerification(state.user); toast("Verification email requested."); }); return; }
  if (action === "copy-ref") {
    const code = state.overview.partner.referralCode;
    if (!configured || !/^BB-[A-F0-9]{12}$/.test(code)) return;
    const base = buttonEl.dataset.kind === "supplier" ? "https://suppliers.batchboss.co.za/?ref=" : "https://app.batchboss.co.za/?signup=1&ref=";
    await navigator.clipboard.writeText(base + encodeURIComponent(code));
    toast("Link copied. Attribution is not live yet."); return;
  }
  if (action === "copy-pitch") {
    await navigator.clipboard.writeText("Good day! I’m a BatchBoss Sales Partner. BatchBoss helps South African bakers manage recipes, costing, stock, quotes and invoices. Would you like more information about our plans?");
    toast("Sales message copied."); return;
  }
  if (action === "review") {
    const decision = buttonEl.dataset.decision;
    if (!confirm("Change this applicant to " + decision + "?")) return;
    await perform(async () => {
      await call("adminReviewPartner", { partnerUid: buttonEl.dataset.uid, decision });
      await loadAdmin(); toast("Partner status updated.");
    });
  }
});
root.addEventListener("submit", async event => {
  const form = event.target.closest("form[data-form]");
  if (!form) return;
  event.preventDefault();
  const data = Object.fromEntries(new FormData(form).entries());
  const kind = form.dataset.form;
  if (kind === "signin") {
    await perform(async () => { await firebase.signInWithEmailAndPassword(firebase.auth, data.email, data.password); });
  } else if (kind === "register") {
    await perform(async () => {
      const result = await firebase.createUserWithEmailAndPassword(firebase.auth, data.email, data.password);
      await firebase.sendEmailVerification(result.user);
      toast("Account created. Check your email for verification.");
    });
  } else if (kind === "apply") {
    await perform(async () => {
      await call("applyPartner", { fullName: data.fullName, province: data.province,
        acceptProgrammeReview: data.acceptProgrammeReview === "on" });
      await refresh(); toast("Application submitted.");
    });
  } else if (kind === "add-lead") {
    await perform(async () => {
      await call("createPartnerLead", { businessName: data.businessName, businessEmail: data.businessEmail,
        province: data.province, leadType: data.leadType, permissionConfirmed: data.permissionConfirmed === "on" });
      await refresh(); toast("Business lead added.");
    });
  } else if (kind === "update-lead") {
    await perform(async () => {
      await call("updatePartnerLead", { leadId: data.leadId, agentStage: data.agentStage, note: data.note });
      await refresh(); toast("Follow-up saved.");
    });
  } else if (kind === "verified-sale") {
    const cents = Math.round(Number(data.amount) * 100);
    if (!Number.isSafeInteger(cents) || cents <= 0) { showError("Enter a valid positive amount."); return; }
    if (!confirm("Have you independently confirmed this REAL payment in the merchant dashboard?")) return;
    await perform(async () => {
      await call("adminRecordVerifiedSale", { leadId: data.leadId, planId: data.planId,
        provider: data.provider, reference: data.reference, amountCents: cents,
        merchantPaymentConfirmed: data.merchantPaymentConfirmed === "on" });
      await loadAdmin(); toast("Pending commission created.");
    });
  } else if (kind === "commission-change") {
    if (!confirm("Confirm change to " + data.nextStatus + "? Paid means the money was already transferred manually.")) return;
    await perform(async () => {
      await call("adminChangeCommissionStatus", { commissionId: data.commissionId,
        nextStatus: data.nextStatus, note: data.note });
      await loadAdmin(); toast("Commission ledger updated.");
    });
  }
});
async function boot() {
  if (!configured) { state.loading = false; render(); return; }
  try {
    const [appMod, authMod, functionsMod] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js"),
      import("https://www.gstatic.com/firebasejs/11.6.0/firebase-functions.js")
    ]);
    const app = appMod.initializeApp(firebaseConfig);
    firebase = {
      auth: authMod.getAuth(app), functions: functionsMod.getFunctions(app, "us-central1"),
      httpsCallable: functionsMod.httpsCallable, authSignOut: authMod.signOut,
      signInWithEmailAndPassword: authMod.signInWithEmailAndPassword,
      createUserWithEmailAndPassword: authMod.createUserWithEmailAndPassword,
      sendEmailVerification: authMod.sendEmailVerification
    };
    authMod.onAuthStateChanged(firebase.auth, async user => { state.user = user; await refresh(); });
  } catch (error) { state.loading = false; showError("Unable to initialise Firebase: " + error.message); }
}
boot();
