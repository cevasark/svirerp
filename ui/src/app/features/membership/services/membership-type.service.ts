import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { MembershipType } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class MembershipTypeService extends ResourceService<MembershipType> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('membership-types');
  }

  /** List is org-scoped (unlike get/create/update/delete, which are flat). */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<MembershipType>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<MembershipType>>(
      `${this.orgScopedEnv.apiUrl}/membership-types`,
      { params: p },
    );
  }
}
