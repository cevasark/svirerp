import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { Vendor } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class VendorService extends ResourceService<Vendor> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('vendors');
  }

  /** List is org-scoped (unlike get/create/update/delete, which are flat). */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<Vendor>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<Vendor>>(
      `${this.orgScopedEnv.apiUrl}/vendors`,
      { params: p },
    );
  }
}
