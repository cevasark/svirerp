import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { ZeffyCampaignMapping, ZeffyImportBatch, ZeffyImportRow } from '../../../core/models/domain.model';
import {
  Page,
  PageParams,
  DEFAULT_PAGE_PARAMS,
  ZeffyImportSummary,
  ZeffyImportCommitResult,
  ZeffyCampaignMappingRequest,
  ReprocessMembershipResult,
} from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class ZeffyImportService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  getBatchesForOrg(orgId: string, params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<ZeffyImportBatch>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<ZeffyImportBatch>>(
      `${this.env.apiUrl}/organizations/${orgId}/zeffy-imports`,
      { params: p },
    );
  }

  preview(orgId: string, file: File): Observable<ZeffyImportBatch> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ZeffyImportBatch>(
      `${this.env.apiUrl}/organizations/${orgId}/zeffy-imports/preview`,
      formData,
    );
  }

  getBatch(batchId: string): Observable<ZeffyImportBatch> {
    return this.http.get<ZeffyImportBatch>(`${this.env.apiUrl}/zeffy-imports/${batchId}`);
  }

  getSummary(batchId: string): Observable<ZeffyImportSummary> {
    return this.http.get<ZeffyImportSummary>(`${this.env.apiUrl}/zeffy-imports/${batchId}/summary`);
  }

  getRows(batchId: string, params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<ZeffyImportRow>> {
    const p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    return this.http.get<Page<ZeffyImportRow>>(
      `${this.env.apiUrl}/zeffy-imports/${batchId}/rows`,
      { params: p },
    );
  }

  commit(orgId: string, batchId: string): Observable<ZeffyImportCommitResult> {
    return this.http.post<ZeffyImportCommitResult>(
      `${this.env.apiUrl}/organizations/${orgId}/zeffy-imports/${batchId}/commit`,
      {},
    );
  }

  getMappingsForOrg(orgId: string): Observable<ZeffyCampaignMapping[]> {
    return this.http.get<ZeffyCampaignMapping[]>(
      `${this.env.apiUrl}/organizations/${orgId}/zeffy-campaign-mappings`,
    );
  }

  upsertMappings(orgId: string, requests: ZeffyCampaignMappingRequest[]): Observable<ZeffyCampaignMapping[]> {
    return this.http.post<ZeffyCampaignMapping[]>(
      `${this.env.apiUrl}/organizations/${orgId}/zeffy-campaign-mappings/bulk`,
      requests,
    );
  }

  deleteMapping(id: string): Observable<void> {
    return this.http.delete<void>(`${this.env.apiUrl}/zeffy-campaign-mappings/${id}`);
  }

  /** One-time backfill for already-committed Ticket rows whose campaign has since been flagged
   *  as a membership payment — safe to call more than once (idempotent on the backend). */
  reprocessMembershipRows(orgId: string): Observable<ReprocessMembershipResult> {
    return this.http.post<ReprocessMembershipResult>(
      `${this.env.apiUrl}/organizations/${orgId}/zeffy-imports/reprocess-membership-rows`,
      {},
    );
  }
}
