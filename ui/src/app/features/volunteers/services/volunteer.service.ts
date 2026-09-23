import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Volunteer } from '../../../core/models/domain.model';
import { Page, PageParams } from '../../../core/models/api.model';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';

@Injectable({ providedIn: 'root' })
export class VolunteerService extends ResourceService<Volunteer> {
  private env = inject(ENVIRONMENT);

  constructor() {
    super('volunteers');
  }

  override getPage(params: PageParams, areaId?: string | null): Observable<Page<Volunteer>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) p = p.set('sort', params.sort);
    if (areaId) p = p.set('areaId', areaId);
    return this.http.get<Page<Volunteer>>(
      `${this.env.apiUrl}/volunteers`,
      { params: p },
    );
  }
}
