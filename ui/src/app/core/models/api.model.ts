/** Spring Data Page response envelope */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number; // 0-based current page index
}

/** Parameters for paginated GET requests */
export interface PageParams {
  page: number;
  size: number;
  sort?: string;
}

export const DEFAULT_PAGE_PARAMS: PageParams = { page: 0, size: 20 };

/** Standard error envelope returned by the Spring GlobalExceptionHandler */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fields?: Record<string, string>; // populated on validation failures
}

/** Response shape of POST /api/members/import — a synthesized
 * batch-operation summary, not a persisted entity, so it lives here rather than
 * in domain.model.ts. */
export interface MemberImportResult {
  created: number;
  updated: number;
  failed: MemberImportRowError[];
}

export interface MemberImportRowError {
  rowNumber: number;
  email: string | null;
  message: string;
}

/** Response shape of GET/PUT /api/settings — admin-only runtime config. `value`
 * is always null for SECRET settings; `hasValue` tells the UI whether one is
 * configured without ever exposing it. */
export interface AppSetting {
  key: string;
  value: string | null;
  valueType: 'STRING' | 'SECRET' | 'BOOLEAN' | 'NUMBER';
  description?: string;
  hasValue: boolean;
}

export interface ZeffySyncRun {
  id: string;
  syncType: 'CAMPAIGNS' | 'PAYMENTS' | 'CONTACTS';
  status: 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';
  triggerType: 'MANUAL' | 'SCHEDULED';
  initiatedBy?: string;
  startedAt: string;
  completedAt?: string;
  requestedFrom?: string;
  requestedTo?: string;
  startingCursor?: string;
  endingCursor?: string;
  executionMode?: 'PREVIEW' | 'APPLY';
  previewRunId?: string;
  fetchedCount: number;
  insertedCount: number;
  updatedCount: number;
  ignoredCount: number;
  failedCount: number;
  alreadyAppliedCount: number;
  eligibleCount: number;
  needsMappingCount: number;
  needsReviewCount: number;
  processedCount: number;
  errorSummary?: string;
}

export interface ZeffyIntegrationStatus {
  apiKeyConfigured: boolean;
  webhookSecretConfigured: boolean;
  integrationMode: 'DISABLED' | 'RECORD_ONLY' | 'LIVE';
  webhookPath: string;
  campaignCount: number;
  confirmedMappingCount: number;
  latestCampaignSync?: ZeffySyncRun;
  latestPaymentPreview?: ZeffySyncRun;
  latestPaymentSync?: ZeffySyncRun;
}

export interface ZeffyConfigurationRequest {
  apiKey?: string;
  webhookSigningSecret?: string;
  validateApiKey: boolean;
  integrationMode?: 'DISABLED' | 'RECORD_ONLY' | 'LIVE';
}

export interface ZeffyWebhookEventFilters {
  eventType?: string;
  status?: string;
  resourceId?: string;
  receivedFrom?: string;
  receivedTo?: string;
}

export interface ZeffyConnectionTestResult {
  connected: boolean;
  testedAt: string;
}

export interface ZeffyCampaignSyncResult {
  run: ZeffySyncRun;
}

export interface ZeffyPaymentSyncRequest {
  mode: 'PREVIEW' | 'APPLY';
  createdFrom?: string;
  createdThrough?: string;
  previewRunId?: string;
}

export interface ZeffyPaymentSyncResult {
  run: ZeffySyncRun;
}

export interface ZeffyPaymentSyncItem {
  id: string;
  zeffyPaymentId: string;
  zeffyPaymentRecordId?: string;
  outcome: 'ELIGIBLE' | 'ALREADY_APPLIED' | 'PROCESSED' | 'IGNORED' | 'NEEDS_MAPPING'
    | 'NEEDS_REVIEW' | 'CHANGED_AFTER_PREVIEW' | 'ERROR';
  detail?: string;
  payloadSha256: string;
  observedAt: string;
  amount?: number;
  currency?: string;
  campaignTitle?: string;
  buyerEmail?: string;
}

export interface ZeffyCampaignMappingRequest {
  action: 'APPLY' | 'IGNORE';
  fundId?: string;
  categoryAccountId?: string;
  grantsMembershipCredit: boolean;
  note?: string;
}

/** Response shape of POST /api/members/recompute-tiers. */
export interface RecomputeTiersResult {
  membersProcessed: number;
}

/** Response shape of POST /api/members/rebuild-from-zeffy. */
export interface ZeffyMembershipRebuildResult {
  paymentsScanned: number;
  peopleCreated: number;
  membersCreated: number;
  membershipPaymentsCreated: number;
  membershipPaymentsUpdated: number;
  membersRecomputed: number;
  needsReview: number;
}

export interface ZeffyApplyPendingCorrectionsResult {
  paymentsScanned: number;
  correctionsApplied: number;
  alreadyCorrected: number;
  needsReview: number;
  failed: number;
}

/** Response shape of GET /api/members/summary. Followers have no
 *  active/inactive split — they never expire (see TierCalculator on the backend). */
export interface MemberSummary {
  activeMembers: number;
  inactiveMembers: number;
  activeBenefactors: number;
  inactiveBenefactors: number;
  followers: number;
  totalMembers: number;
}

/** Request body for POST /api/stripe-product-mappings and
 * PUT /api/stripe-product-mappings/{id}. */
export interface StripeProductMappingRequest {
  stripePriceId: string;
  displayName?: string;
  purpose: 'membership_dues' | 'service_request' | 'event_ticket' | 'general_income';
  fundId?: string;
  categoryAccountId?: string;
  serviceType?: string;
}

/** Response shape of GET /api/stripe-prices — Prices pulled live from the
 * connected Stripe account, so a mapping can be created before anything has ever been paid for
 * through svirerp yet. amount/unitAmount is in the smallest currency unit (cents), as Stripe returns it. */
export interface StripePriceInfo {
  priceId: string;
  displayName: string;
  unitAmount?: number;
  currency?: string;
  alreadyMapped: boolean;
}
