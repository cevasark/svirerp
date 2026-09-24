import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Person, PersonOverview } from '../../../core/models/domain.model';
import { Page, PageParams } from '../../../core/models/api.model';
import { ResourceService } from '../../../core/services/resource.service';

@Injectable({ providedIn: 'root' })
export class PersonService extends ResourceService<Person> {
  constructor() {
    super('persons');
  }

  /** Autocomplete search — case-insensitive "contains" match against one allow-listed field
   *  (currently firstName/lastName, see PersonService#SEARCHABLE_FIELDS on the backend). */
  search(field: string, q: string): Observable<Person[]> {
    const params = new HttpParams().set('field', field).set('q', q);
    return this.http.get<Person[]>(this.endpoint('/search'), { params });
  }

  getOverviewPage(params: PageParams): Observable<Page<PersonOverview>> {
    let query = new HttpParams().set('page', params.page).set('size', params.size);
    if (params.sort) query = query.set('sort', params.sort);
    return this.http.get<Page<PersonOverview>>(this.endpoint('/overview'), { params: query });
  }
}
