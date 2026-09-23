import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { CalendarEvent } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class EventService extends ResourceService<CalendarEvent> {
  private readonly env = inject(ENVIRONMENT);

  constructor() {
    super('events');
  }

  /** List all events in this installation. */
  override getPage(params: PageParams = DEFAULT_PAGE_PARAMS, from?: string | null, to?: string | null): Observable<Page<CalendarEvent>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (from) {
      p = p.set('from', from);
    }
    if (to) {
      p = p.set('to', to);
    }
    return this.http.get<Page<CalendarEvent>>(
      `${this.env.apiUrl}/events`,
      { params: p },
    );
  }
}
