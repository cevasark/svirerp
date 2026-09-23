import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { MeetingMinutes } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class MeetingMinutesService extends ResourceService<MeetingMinutes> {
  private readonly apiEnv = inject(ENVIRONMENT);

  constructor() {
    super('meeting-minutes');
  }

  /** List all meeting minutes in this installation. */
  override getPage(
    params: PageParams = DEFAULT_PAGE_PARAMS,
    fromDate?: string | null,
    openActionItemsOnly = false,
  ): Observable<Page<MeetingMinutes>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (fromDate) {
      p = p.set('fromDate', fromDate);
    }
    if (openActionItemsOnly) {
      p = p.set('openActionItemsOnly', 'true');
    }
    return this.http.get<Page<MeetingMinutes>>(
      `${this.apiEnv.apiUrl}/meeting-minutes`,
      { params: p },
    );
  }
}
