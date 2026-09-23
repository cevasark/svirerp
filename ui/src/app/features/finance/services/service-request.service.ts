import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { ServiceRequest } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class ServiceRequestService extends ResourceService<ServiceRequest> {
  private readonly apiEnv = inject(ENVIRONMENT);

  constructor() {
    super('service-requests');
  }

  /** List all service requests in this installation. */
  override getPage(
    params: PageParams = DEFAULT_PAGE_PARAMS,
  ): Observable<Page<ServiceRequest>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<ServiceRequest>>(
      `${this.apiEnv.apiUrl}/service-requests`,
      { params: p },
    );
  }

  /** Amount still owed: agreedAmount minus every posted payment tagged with this request. */
  balance(id: string): Observable<number> {
    return this.http.get<number>(this.endpoint(`/${id}/balance`));
  }
}
