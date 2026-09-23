import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { Member } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS, MemberImportResult, RecomputeTiersResult, MemberSummary } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class MemberService extends ResourceService<Member> {
  private readonly apiEnv = inject(ENVIRONMENT);

  constructor() {
    super('members');
  }

  /** List all members in this installation. */
  override getPage(
    params: PageParams = DEFAULT_PAGE_PARAMS,
    status?: string | null,
    membershipTypeId?: string | null,
  ): Observable<Page<Member>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (status) {
      p = p.set('status', status);
    }
    if (membershipTypeId) {
      p = p.set('membershipTypeId', membershipTypeId);
    }
    return this.http.get<Page<Member>>(
      `${this.apiEnv.apiUrl}/members`,
      { params: p },
    );
  }

  getSummary(): Observable<MemberSummary> {
    return this.http.get<MemberSummary>(
      `${this.apiEnv.apiUrl}/members/summary`,
    );
  }

  downloadImportTemplate(): Observable<Blob> {
    return this.http.get(
      `${this.apiEnv.apiUrl}/members/import-template`,
      { responseType: 'blob' },
    );
  }

  importMembers(file: File): Observable<MemberImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<MemberImportResult>(
      `${this.apiEnv.apiUrl}/members/import`,
      formData,
    );
  }

  /** Re-runs tier computation for every member — tier can go stale purely from time passing
   *  (a qualifying payment ages past its window with no new payment event). */
  recomputeTiers(): Observable<RecomputeTiersResult> {
    return this.http.post<RecomputeTiersResult>(
      `${this.apiEnv.apiUrl}/members/recompute-tiers`,
      {},
    );
  }
}
