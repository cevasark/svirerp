import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { MemberPayment } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class MemberPaymentService extends ResourceService<MemberPayment> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('member-payments');
  }

  /** Org-wide list, for the Contributions tab. */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS, fromDate?: string | null): Observable<Page<MemberPayment>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (fromDate) {
      p = p.set('fromDate', fromDate);
    }
    return this.http.get<Page<MemberPayment>>(
      `${this.orgScopedEnv.apiUrl}/member-payments`,
      { params: p },
    );
  }

  /** Single member's contribution history, for the Member Detail page. */
  getForMember(memberId: string, params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<MemberPayment>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<MemberPayment>>(
      `${this.orgScopedEnv.apiUrl}/members/${memberId}/payments`,
      { params: p },
    );
  }
}
