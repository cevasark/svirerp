import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { StripeProductMapping, StripeWebhookEvent } from '../../../core/models/domain.model';
import {
  Page,
  PageParams,
  DEFAULT_PAGE_PARAMS,
  StripeProductMappingRequest,
  StripePriceInfo,
} from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class StripeIntegrationService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  // ── Product mappings ─────────────────────────────────────────────────────

  getMappings(): Observable<StripeProductMapping[]> {
    return this.http.get<StripeProductMapping[]>(
      `${this.env.apiUrl}/stripe-product-mappings`,
    );
  }

  getStripePrices(): Observable<StripePriceInfo[]> {
    return this.http.get<StripePriceInfo[]>(`${this.env.apiUrl}/stripe-prices`);
  }

  createMapping(request: StripeProductMappingRequest): Observable<StripeProductMapping> {
    return this.http.post<StripeProductMapping>(
      `${this.env.apiUrl}/stripe-product-mappings`,
      request,
    );
  }

  updateMapping(id: string, request: StripeProductMappingRequest): Observable<StripeProductMapping> {
    return this.http.put<StripeProductMapping>(`${this.env.apiUrl}/stripe-product-mappings/${id}`, request);
  }

  deleteMapping(id: string): Observable<void> {
    return this.http.delete<void>(`${this.env.apiUrl}/stripe-product-mappings/${id}`);
  }

  // ── Events ───────────────────────────────────────────────────────────────

  getEvents(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<StripeWebhookEvent>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<StripeWebhookEvent>>(
      `${this.env.apiUrl}/stripe-events`,
      { params: p },
    );
  }

  reprocessEvent(id: string): Observable<StripeWebhookEvent> {
    return this.http.post<StripeWebhookEvent>(`${this.env.apiUrl}/stripe-events/${id}/reprocess`, {});
  }
}
