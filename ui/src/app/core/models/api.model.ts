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

/** Response shape of POST /api/organizations/{orgId}/members/import — a synthesized
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

/** Response shape of POST /api/organizations/{orgId}/persons/import — imports a Zeffy contacts
 * export, enrolling anyone not already in the system as an active/inactive Follower. Rows whose
 * email already matches an existing Person are skipped, not updated. */
export interface PersonImportResult {
  created: number;
  skippedExisting: number;
  failed: PersonImportRowError[];
}

export interface PersonImportRowError {
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

/** Response shape of GET /api/zeffy-imports/{batchId}/summary — a synthesized preview
 * projection, not a persisted entity. */
export interface ZeffyImportSummary {
  batchId: string;
  totalRows: number;
  readyCount: number;
  duplicateCount: number;
  skippedStatusCount: number;
  unmappedCampaignCount: number;
  errorCount: number;
  committedCount: number;
  newPersonCount: number;
  newMemberCount: number;
  totalAmountReady: number;
  unmappedCampaignTitles: string[];
}

/** Response shape of POST /api/organizations/{orgId}/zeffy-imports/{batchId}/commit. */
export interface ZeffyImportCommitResult {
  batchId: string;
  committed: number;
  failed: number;
  stillUnmappedCampaign: number;
}

/** Request body for POST /api/organizations/{orgId}/zeffy-campaign-mappings/bulk. */
export interface ZeffyCampaignMappingRequest {
  campaignTitle: string;
  fundId: string;
  isMembershipPayment: boolean;
}

/** Response shape of POST /api/organizations/{orgId}/zeffy-imports/reprocess-membership-rows —
 *  the one-time backfill for Ticket rows whose campaign was flagged as a membership payment
 *  after they'd already committed. */
export interface ReprocessMembershipResult {
  rowsProcessed: number;
  membersCreated: number;
}

/** Response shape of POST /api/organizations/{orgId}/members/recompute-tiers. */
export interface RecomputeTiersResult {
  membersProcessed: number;
}

/** Response shape of GET /api/organizations/{orgId}/members/summary. Followers have no
 *  active/inactive split — they never expire (see TierCalculator on the backend). */
export interface MemberSummary {
  activeMembers: number;
  inactiveMembers: number;
  activeBenefactors: number;
  inactiveBenefactors: number;
  followers: number;
  totalMembers: number;
}

/** Request body for POST /api/organizations/{orgId}/stripe-product-mappings and
 * PUT /api/stripe-product-mappings/{id}. */
export interface StripeProductMappingRequest {
  stripePriceId: string;
  displayName?: string;
  purpose: 'membership_dues' | 'service_request' | 'event_ticket' | 'general_income';
  fundId?: string;
  categoryAccountId?: string;
  serviceType?: string;
}

/** Response shape of GET /api/organizations/{orgId}/stripe-prices — Prices pulled live from the
 * connected Stripe account, so a mapping can be created before anything has ever been paid for
 * through svirerp yet. amount/unitAmount is in the smallest currency unit (cents), as Stripe returns it. */
export interface StripePriceInfo {
  priceId: string;
  displayName: string;
  unitAmount?: number;
  currency?: string;
  alreadyMapped: boolean;
}
