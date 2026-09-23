import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { Trustee } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class TrusteeService extends ResourceService<Trustee> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('trustees');
  }

  /** List is org-scoped (unlike get/create/update/delete, which are flat). */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<Trustee>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<Trustee>>(
      `${this.orgScopedEnv.apiUrl}/trustees`,
      { params: p },
    );
  }

  renew(id: string): Observable<Trustee> {
    return this.http.post<Trustee>(this.endpoint(`/${id}/renew`), {});
  }
}
