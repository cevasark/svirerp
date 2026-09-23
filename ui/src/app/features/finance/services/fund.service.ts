import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { Fund, FundSummary } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class FundService extends ResourceService<Fund> {
  private readonly env = inject(ENVIRONMENT);

  constructor() {
    super('funds');
  }

  /** List all funds in this installation. */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<Fund>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    return this.http.get<Page<Fund>>(
      `${this.env.apiUrl}/funds`,
      { params: p },
    );
  }

  /** Powers the "project financial status" view. */
  summary(id: string): Observable<FundSummary> {
    return this.http.get<FundSummary>(this.endpoint(`/${id}/summary`));
  }
}
