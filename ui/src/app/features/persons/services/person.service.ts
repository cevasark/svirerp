import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Person } from '../../../core/models/domain.model';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { PersonImportResult } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class PersonService extends ResourceService<Person> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('persons');
  }

  /** Autocomplete search — case-insensitive "contains" match against one allow-listed field
   *  (currently firstName/lastName, see PersonService#SEARCHABLE_FIELDS on the backend). */
  search(field: string, q: string): Observable<Person[]> {
    const params = new HttpParams().set('field', field).set('q', q);
    return this.http.get<Person[]>(this.endpoint('/search'), { params });
  }

  /** Imports a Zeffy contacts export — catches the free ($0) "Follower" registrations that never
   *  show up in a Zeffy Transactions import, since they never produced a financial transaction. */
  downloadImportTemplate(orgId: string): Observable<Blob> {
    return this.http.get(
      `${this.orgScopedEnv.apiUrl}/organizations/${orgId}/persons/import-template`,
      { responseType: 'blob' },
    );
  }

  importPeople(orgId: string, file: File): Observable<PersonImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PersonImportResult>(
      `${this.orgScopedEnv.apiUrl}/organizations/${orgId}/persons/import`,
      formData,
    );
  }
}
