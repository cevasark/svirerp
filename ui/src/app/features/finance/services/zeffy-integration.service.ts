import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import {
  ZeffyCampaign, ZeffyPaymentLifecycle, ZeffyWebhookEvent,
} from '../../../core/models/domain.model';
import {
  Page,
  PageParams,
  ZeffyCampaignMappingRequest,
  ZeffyCampaignSyncResult,
  ZeffyConfigurationRequest,
  ZeffyConnectionTestResult,
  ZeffyIntegrationStatus,
  ZeffyPaymentSyncItem,
  ZeffyPaymentSyncRequest,
  ZeffyPaymentSyncResult,
  ZeffySyncRun,
  ZeffyWebhookEventFilters,
} from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class ZeffyIntegrationService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  getStatus(): Observable<ZeffyIntegrationStatus> {
    return this.http.get<ZeffyIntegrationStatus>(`${this.env.apiUrl}/settings/zeffy/status`);
  }

  saveConfiguration(request: ZeffyConfigurationRequest): Observable<ZeffyIntegrationStatus> {
    return this.http.put<ZeffyIntegrationStatus>(
      `${this.env.apiUrl}/settings/zeffy/configuration`, request,
    );
  }

  testConnection(): Observable<ZeffyConnectionTestResult> {
    return this.http.post<ZeffyConnectionTestResult>(
      `${this.env.apiUrl}/settings/zeffy/test-connection`, {},
    );
  }

  syncCampaigns(): Observable<ZeffyCampaignSyncResult> {
    return this.http.post<ZeffyCampaignSyncResult>(
      `${this.env.apiUrl}/settings/zeffy/sync-campaigns`, {},
    );
  }

  syncPayments(request: ZeffyPaymentSyncRequest): Observable<ZeffyPaymentSyncResult> {
    return this.http.post<ZeffyPaymentSyncResult>(
      `${this.env.apiUrl}/settings/zeffy/sync-payments`, request,
    );
  }

  getPaymentSyncResults(runId: string, params: PageParams): Observable<Page<ZeffyPaymentSyncItem>> {
    let query = new HttpParams().set('page', params.page).set('size', params.size);
    if (params.sort) query = query.set('sort', params.sort);
    return this.http.get<Page<ZeffyPaymentSyncItem>>(
      `${this.env.apiUrl}/settings/zeffy/sync-runs/${runId}/payment-results`, { params: query },
    );
  }

  getSyncRuns(params: PageParams, syncType?: string): Observable<Page<ZeffySyncRun>> {
    let query = new HttpParams()
      .set('page', params.page)
      .set('size', params.size);
    if (params.sort) query = query.set('sort', params.sort);
    if (syncType) query = query.set('syncType', syncType);
    return this.http.get<Page<ZeffySyncRun>>(
      `${this.env.apiUrl}/settings/zeffy/sync-runs`, { params: query },
    );
  }

  getCampaigns(params: PageParams): Observable<Page<ZeffyCampaign>> {
    let query = new HttpParams()
      .set('page', params.page)
      .set('size', params.size);
    if (params.sort) query = query.set('sort', params.sort);
    return this.http.get<Page<ZeffyCampaign>>(`${this.env.apiUrl}/zeffy-campaigns`, { params: query });
  }

  updateCampaignMapping(
    campaignId: string,
    request: ZeffyCampaignMappingRequest,
  ): Observable<ZeffyCampaign> {
    return this.http.put<ZeffyCampaign>(
      `${this.env.apiUrl}/zeffy-campaigns/${encodeURIComponent(campaignId)}/mapping`, request,
    );
  }

  getWebhookEvents(
    pageParams: PageParams,
    filters: ZeffyWebhookEventFilters,
  ): Observable<Page<ZeffyWebhookEvent>> {
    let params = new HttpParams()
      .set('page', pageParams.page)
      .set('size', pageParams.size);
    if (pageParams.sort) params = params.set('sort', pageParams.sort);
    if (filters.eventType) params = params.set('eventType', filters.eventType);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.resourceId) params = params.set('resourceId', filters.resourceId);
    if (filters.receivedFrom) params = params.set('receivedFrom', filters.receivedFrom);
    if (filters.receivedTo) params = params.set('receivedTo', filters.receivedTo);
    return this.http.get<Page<ZeffyWebhookEvent>>(
      `${this.env.apiUrl}/zeffy-webhook-events`, { params },
    );
  }

  reprocessWebhookEvent(id: string): Observable<ZeffyWebhookEvent> {
    return this.http.post<ZeffyWebhookEvent>(
      `${this.env.apiUrl}/zeffy-webhook-events/${id}/reprocess`, {},
    );
  }

  getWebhookEventLifecycle(id: string): Observable<ZeffyPaymentLifecycle> {
    return this.http.get<ZeffyPaymentLifecycle>(
      `${this.env.apiUrl}/zeffy-webhook-events/${id}/lifecycle`,
    );
  }
}
