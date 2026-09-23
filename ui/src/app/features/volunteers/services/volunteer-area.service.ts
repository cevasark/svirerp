import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { VolunteerArea } from '../../../core/models/domain.model';
import { Page, PageParams } from '../../../core/models/api.model';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';

@Injectable({ providedIn: 'root' })
export class VolunteerAreaService extends ResourceService<VolunteerArea> {
  private env = inject(ENVIRONMENT);

  constructor() {
    super('volunteer-areas');
  }

  override getPage(params: PageParams): Observable<Page<VolunteerArea>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) p = p.set('sort', params.sort);
    return this.http.get<Page<VolunteerArea>>(
      `${this.env.apiUrl}/volunteer-areas`,
      { params: p },
    );
  }
}
