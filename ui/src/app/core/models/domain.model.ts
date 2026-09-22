// ─── Person ────────────────────────────────────────────────────────────────
export interface Person {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  addressLine1?: string;
  city?: string;
  state?: string;
  zip?: string;
  dateOfBirth?: string;
  createdAt?: string;
}

// ─── Organization ───────────────────────────────────────────────────────────
export interface Organization {
  id: string;
  name: string;
  legalName?: string;
  taxIdEin?: string;
  nonprofitType?: string;
  missionStatement?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
  phone?: string;
  email?: string;
  website?: string;
  foundedDate?: string;
  fiscalYearStart?: string;
  logoUrl?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ─── Membership ─────────────────────────────────────────────────────────────
// Entities have no DTO layer and serialize directly, so relations come back
// as full nested objects (not flat *Id strings) — e.g. `org: {...}`, not `orgId`.
export interface MembershipType {
  id: string;
  org: Organization;
  name: string;
  description?: string;
  annualFee: number;
  durationMonths: number;
  isActive: boolean;
  canVote: boolean;
  benefits?: string;
  maxMembers?: number;
  createdAt?: string;
}

export interface Member {
  id: string;
  person: Person;
  org: Organization;
  membershipType: MembershipType;
  memberNumber?: string;
  joinDate: string;
  expiryDate?: string;
  status: 'active' | 'inactive' | 'suspended' | 'expired' | 'pending';
  emailOptIn: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface MemberPayment {
  id: string;
  member: Member;
  amount: number;
  paymentDate: string;
  paymentMethod?: 'cash' | 'check' | 'credit_card' | 'ach' | 'online' | 'other' | 'zeffy' | 'stripe';
  transactionRef?: string;
  periodStart?: string;
  periodEnd?: string;
  status: 'pending' | 'completed' | 'failed' | 'refunded';
  notes?: string;
}

// ─── Governance ─────────────────────────────────────────────────────────────
export interface Trustee {
  id: string;
  person: Person;
  org: Organization;
  title?: string;
  role: string;
  termStart: string;
  termEnd?: string;
  isActive: boolean;
  isOfficer: boolean;
  notes?: string;
  createdAt?: string;
}

export interface TrusteeDocument {
  id: string;
  trustee: Trustee;
  documentType: string;
  fileUrl: string;
  uploadedAt?: string;
  notes?: string;
}

export interface Committee {
  id: string;
  orgId: string;
  name: string;
  description?: string;
  isActive?: boolean;
}

export interface CommitteeMeeting {
  id: string;
  committeeId: string;
  title: string;
  meetingDate: string;
  location?: string;
  meetingType: 'regular' | 'special' | 'emergency' | 'annual';
  status: 'scheduled' | 'completed' | 'cancelled' | 'postponed';
}

export interface CommitteeResolution {
  id: string;
  committeeId: string;
  meetingId?: string;
  title: string;
  description?: string;
  status: 'passed' | 'failed' | 'tabled' | 'withdrawn';
  votedAt?: string;
}

// General board/trustee meeting record — org-level, not tied to a committee.
export interface MeetingMinutes {
  id: string;
  org: Organization;
  meetingDate: string;
  title: string;
  summary?: string;
  createdAt?: string;
}

export interface ActionItem {
  id: string;
  meetingMinutes: MeetingMinutes;
  assigneeTrustee?: Trustee;
  note: string;
  priority: 'high' | 'normal' | 'low';
  dueDate?: string;
  status: 'new' | 'planned' | 'done';
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ─── Governance: Projects ───────────────────────────────────────────────────
// Task-tracking projects under Governance — distinct from Finance's Fund/"project"
// restricted-fund-accounting concept (see the Fund interface below).

export interface Project {
  id: string;
  org: Organization;
  name: string;
  description?: string;
  status: 'planning' | 'in_progress' | 'on_hold' | 'completed' | 'cancelled';
  dueDate?: string;
  assignee?: Person;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProjectTask {
  id: string;
  project: Project;
  name: string;
  description?: string;
  status: 'todo' | 'in_progress' | 'blocked' | 'done';
  assignee?: Person;
  createdAt?: string;
  updatedAt?: string;
}

/** authorName is always stamped server-side from the logged-in session — see
 *  GovernanceController#resolveAuthorName on the backend; never settable from the client. */
export interface ProjectComment {
  id: string;
  project: Project;
  comment: string;
  authorName: string;
  createdAt?: string;
}

export interface ProjectTaskComment {
  id: string;
  projectTask: ProjectTask;
  comment: string;
  authorName: string;
  createdAt?: string;
}

/** A checklist attached to a Project — a sibling of ProjectTask, not nested under one. A project
 *  can hold multiple independent checklists (see ProjectChecklist.java's class doc). */
export interface ProjectChecklist {
  id: string;
  project: Project;
  title: string;
  completionDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** Deliberately 3-state, not a boolean checkbox — see ProjectChecklistItem.java's class doc.
 *  `detail` is an optional one-line note captured when marking Done/Skipped; Re-open always
 *  clears it (enforced server-side, not just in the UI). */
export interface ProjectChecklistItem {
  id: string;
  checklist: ProjectChecklist;
  text: string;
  detail?: string;
  status: 'new' | 'done' | 'skipped';
  createdAt?: string;
  updatedAt?: string;
}

// ─── Events ─────────────────────────────────────────────────────────────────
export interface CalendarEvent {
  id: string;
  org: Organization;
  createdBy?: Person;
  title: string;
  description?: string;
  eventType?: string;
  startDatetime: string;
  endDatetime?: string;
  isAllDay?: boolean;
  location?: string;
  virtualLink?: string;
  isRecurring?: boolean;
  recurrenceRule?: string;
  status: 'scheduled' | 'cancelled' | 'completed' | 'postponed';
  visibility: 'public' | 'members_only' | 'internal';
  capacity?: number;
  publishToOfficial?: boolean;
  googleOfficialEventId?: string;
  googleOfficialSyncError?: string;
  publishToInternal?: boolean;
  googleInternalEventId?: string;
  googleInternalSyncError?: string;
  createdAt?: string;
}

export interface ChurchEvent {
  id: string;
  calendarEvent: CalendarEvent;
  serviceType?: string;
  liturgicalSeason?: string;
  officiant?: string;
  sermonTitle?: string;
  scriptureReadings?: string;
  musicSelections?: string;
  specialInstructions?: string;
  offeringCollected?: number;
  attendanceCount?: number;
}

export interface EventRegistration {
  id: string;
  event: CalendarEvent;
  person: Person;
  registeredAt?: string;
  status: 'registered' | 'attended' | 'cancelled' | 'waitlisted' | 'no_show';
  feePaid?: number;
  ticketNumber?: string;
  notes?: string;
}

export interface EventResource {
  id: string;
  event: CalendarEvent;
  resourceType: string;
  resourceName: string;
  assignedTo?: string;
  notes?: string;
}

// ─── Volunteers ──────────────────────────────────────────────────────────────
export interface VolunteerArea {
  id: string;
  org: Organization;
  name: string;
  description?: string;
  isActive?: boolean;
}

export interface Volunteer {
  id: string;
  person: Person;
  org: Organization;
  contactPerson?: Person;
  onboardDate: string;
  isActive?: boolean;
  skills?: string;
  availability?: string;
  notes?: string;
  areas?: VolunteerArea[];
}

export interface VolunteerHour {
  id: string;
  volunteerId: string;
  date: string;
  hours: number;
  description?: string;
  status: 'pending' | 'approved' | 'rejected';
  eventId?: string;
}

// ─── Finance ─────────────────────────────────────────────────────────────────
export interface Fund {
  id: string;
  org: Organization;
  fundName: string;
  fundCode: string;
  fundType: 'unrestricted' | 'temporarily_restricted' | 'permanently_restricted';
  description?: string;
  isRestricted?: boolean;
  restrictionPurpose?: string;
  isActive?: boolean;
  openingBalance: number;
}

/** Powers the "project financial status" view — GET /api/funds/{id}/summary. */
export interface FundSummary {
  openingBalance: number;
  totalIncome: number;
  totalExpense: number;
  balance: number;
}

/** One category row on the Statement of Activities / balance on the Statement of Financial
 *  Position — amounts/balances are always positive dollar figures, already sign-adjusted server-side. */
export interface StatementOfActivitiesLine {
  accountId: string;
  accountNumber: string;
  accountName: string;
  amount: number;
}

export interface StatementOfActivities {
  from: string;
  to: string;
  income: StatementOfActivitiesLine[];
  totalIncome: number;
  expense: StatementOfActivitiesLine[];
  totalExpense: number;
  netChange: number;
}

export interface BalanceSheetLine {
  accountId: string;
  accountNumber: string;
  accountName: string;
  balance: number;
}

/** netIncomeToDate is computed server-side (not stored) — see FinanceService#statementOfFinancialPosition;
 *  this app has no period-closing process, so a real equity account alone wouldn't reflect income
 *  earned so far. totalAssets always equals totalLiabilitiesAndEquity by the double-entry identity. */
export interface StatementOfFinancialPosition {
  asOf: string;
  assets: BalanceSheetLine[];
  totalAssets: number;
  liabilities: BalanceSheetLine[];
  totalLiabilities: number;
  equity: BalanceSheetLine[];
  netIncomeToDate: number;
  totalEquity: number;
  totalLiabilitiesAndEquity: number;
}

/** One fund's financial status, for the all-funds-at-once Funds Overview report. */
export interface FundOverviewRow {
  fundId: string;
  fundName: string;
  fundType: string;
  openingBalance: number;
  totalIncome: number;
  totalExpense: number;
  balance: number;
}

export interface Account {
  id: string;
  org: Organization;
  parentAccount?: Account;
  accountNumber: string;
  accountName: string;
  accountType: 'asset' | 'liability' | 'equity' | 'revenue' | 'expense';
  accountSubtype?: string;
  normalBalance: 'debit' | 'credit';
  isActive?: boolean;
  isSystem?: boolean;
  description?: string;
  createdAt?: string;
}

export interface Vendor {
  id: string;
  org: Organization;
  name: string;
  category?: string;
  contactName?: string;
  phone?: string;
  email?: string;
  addressLine1?: string;
  city?: string;
  state?: string;
  zip?: string;
  notes?: string;
  isActive?: boolean;
  createdAt?: string;
}

export interface ServiceRequest {
  id: string;
  org: Organization;
  requestorPerson?: Person;
  serviceType: 'wedding' | 'baptism' | 'funeral' | 'memorial' | 'blessing' | 'other';
  requestedDate?: string;
  agreedAmount: number;
  status: 'requested' | 'scheduled' | 'completed' | 'cancelled';
  churchEvent?: ChurchEvent;
  notes?: string;
  createdAt?: string;
}

export interface JournalEntry {
  id: string;
  org: Organization;
  createdBy?: Person;
  approvedBy?: Person;
  entryNumber?: string;
  entryDate: string;
  description?: string;
  reference?: string;
  entryType: 'general' | 'adjusting' | 'closing' | 'reversing' | 'opening';
  status: 'draft' | 'posted' | 'void';
  totalDebit: number;
  totalCredit: number;
  // Transaction tags set by the Record Income / Record Expense flow — see RecordIncomeRequest /
  // RecordExpenseRequest. Denormalized for the transaction list; the ledger truth is the lines below.
  paymentMethod?: 'cash' | 'check' | 'zeffy' | 'bank_transfer' | 'card' | 'other' | 'stripe' | 'zelle' | 'facebook';
  checkNumber?: string;
  payer?: Person;
  vendor?: Vendor;
  serviceRequest?: ServiceRequest;
  categoryAccount?: Account;
  fund?: Fund;
  createdAt?: string;
  approvedAt?: string;
}

export interface RecordIncomeRequest {
  entryDate: string;
  amount: number;
  description?: string;
  categoryAccountId: string;
  depositAccountId: string;
  fundId?: string;
  payerId?: string;
  serviceRequestId?: string;
  paymentMethod: 'cash' | 'check' | 'zeffy' | 'bank_transfer' | 'card' | 'other' | 'stripe' | 'zelle' | 'facebook';
  checkNumber?: string;
  /** A processing fee deducted before deposit (e.g. Stripe) — splits the entry into a net deposit
   *  line plus a fee-expense line, both still summing to `amount`. Omit for sources with no fee. */
  feeAmount?: number;
  feeAccountId?: string;
}

export interface RecordExpenseRequest {
  entryDate: string;
  amount: number;
  description?: string;
  categoryAccountId: string;
  paymentAccountId: string;
  fundId?: string;
  vendorId?: string;
  paymentMethod: 'cash' | 'check' | 'zeffy' | 'bank_transfer' | 'card' | 'other' | 'stripe' | 'zelle' | 'facebook';
  checkNumber?: string;
}

/** "Record Platform Payout" — a transfer from a pass-through platform's "Undeposited Funds"
 *  clearing account into Checking once its lump-sum payout actually lands. No category/revenue
 *  account involved; see FinanceService#recordTransfer. */
export interface RecordTransferRequest {
  entryDate: string;
  amount: number;
  description?: string;
  fromAccountId: string;
  toAccountId: string;
}

export interface JournalLine {
  id: string;
  journalEntry: JournalEntry;
  account: Account;
  fund?: Fund;
  debitAmount: number;
  creditAmount: number;
  memo?: string;
}

export interface BankAccount {
  id: string;
  org: Organization;
  glAccount: Account;
  institutionName: string;
  accountName: string;
  accountNumberMasked?: string;
  routingNumberMasked?: string;
  accountType: 'checking' | 'savings' | 'money_market' | 'cd' | 'investment';
  currency?: string;
  currentBalance: number;
  openedDate?: string;
  isActive?: boolean;
  isPrimary?: boolean;
  contactName?: string;
  contactPhone?: string;
  notes?: string;
  updatedAt?: string;
}

export interface BankTransaction {
  id: string;
  bankAccount: BankAccount;
  journalEntry?: JournalEntry;
  transactionRef?: string;
  transactionDate: string;
  postedDate?: string;
  amount: number;
  transactionType: 'debit' | 'credit' | 'check' | 'transfer' | 'fee' | 'interest' | 'other';
  description?: string;
  payee?: string;
  checkNumber?: string;
  status: 'pending' | 'cleared' | 'void';
  isReconciled?: boolean;
  importedAt?: string;
}

export interface BankReconciliation {
  id: string;
  bankAccount: BankAccount;
  reconciledBy?: Person;
  statementDate: string;
  statementBalance: number;
  bookBalance: number;
  difference?: number;
  status: 'in_progress' | 'completed' | 'discrepancy';
  reconciledAt?: string;
  notes?: string;
}

export interface ReconciliationItem {
  id: string;
  reconciliation: BankReconciliation;
  bankTransaction: BankTransaction;
  isCleared: boolean;
  itemType: 'transaction' | 'deposit_in_transit' | 'outstanding_check' | 'adjustment';
  notes?: string;
}

// ─── Zeffy Import ────────────────────────────────────────────────────────────
export interface ZeffyCampaignMapping {
  id: string;
  org: Organization;
  campaignTitle: string;
  fund: Fund;
  /** Overrides the Ticket-category-skips-membership default — Zeffy implements fixed-price
   *  membership registration as a Ticket-type product, not Donation. */
  isMembershipPayment: boolean;
  createdAt?: string;
}

export interface ZeffyImportBatch {
  id: string;
  org: Organization;
  fileName: string;
  uploadedAt?: string;
  status: 'previewed' | 'committed';
  rowCount: number;
  committedAt?: string;
}

/** One row per line of an uploaded Zeffy Transactions export — the preview/commit staging area
 *  and audit trail. */
export interface ZeffyImportRow {
  id: string;
  batch: ZeffyImportBatch;
  org: Organization;
  rowNumber: number;
  transactionId?: string;
  amount?: number;
  /** "Donation" earns membership tier credit; "Ticket" is a plain income posting only. */
  category?: 'Donation' | 'Ticket';
  /** The tax-deductible portion — informational only, blank for non-donation rows. */
  eligibleAmount?: number;
  transactionDate?: string;
  /** When Zeffy pays this out to the bank — informational only. */
  availableDate?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  campaignTitle?: string;
  dedupeKey?: string;
  outcome: 'pending_preview' | 'ready' | 'duplicate' | 'skipped_status' | 'unmapped_campaign' | 'error' | 'committed';
  outcomeDetail?: string;
  isNewPerson: boolean;
  isNewMember: boolean;
  person?: Person;
  member?: Member;
  memberPayment?: MemberPayment;
  journalEntry?: JournalEntry;
  fund?: Fund;
}

// ─── Stripe Integration ──────────────────────────────────────────────────────
/** Routes a Stripe Price to what a completed payment against it means, and where it posts. */
export interface StripeProductMapping {
  id: string;
  org: Organization;
  stripePriceId: string;
  displayName?: string;
  purpose: 'membership_dues' | 'service_request' | 'event_ticket' | 'general_income';
  fund?: Fund;
  categoryAccount?: Account;
  /** Only meaningful when purpose = service_request. */
  serviceType?: 'wedding' | 'baptism' | 'funeral' | 'memorial' | 'blessing' | 'other';
  createdAt?: string;
  updatedAt?: string;
}

/** One row per Stripe webhook delivery — the audit trail linking back to whatever it produced. */
export interface StripeWebhookEvent {
  id: string;
  org: Organization;
  stripeEventId: string;
  eventType: string;
  stripePriceId?: string;
  amount?: number;
  /** Stripe's processing fee (from the underlying charge's BalanceTransaction), when resolvable. */
  fee?: number;
  email?: string;
  firstName?: string;
  lastName?: string;
  status: 'received' | 'processed' | 'needs_mapping' | 'error' | 'ignored';
  errorMessage?: string;
  receivedAt?: string;
  processedAt?: string;
  person?: Person;
  member?: Member;
  memberPayment?: MemberPayment;
  serviceRequest?: ServiceRequest;
  journalEntry?: JournalEntry;
}
