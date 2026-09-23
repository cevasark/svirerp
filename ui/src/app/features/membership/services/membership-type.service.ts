import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { MembershipType } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class MembershipTypeService extends ResourceService<MembershipType> {
  private readonly apiEnv = inject(ENVIRONMENT);

  constructor() {
    super('membership-types');
  }

  /** List all membership types in this installation. */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<MembershipType>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<MembershipType>>(
      `${this.apiEnv.apiUrl}/membership-types`,
      { params: p },
    );
  }
}
