import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Organization } from '../../../core/models/domain.model';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  get(): Observable<Organization> {
    return this.http.get<Organization>(`${this.env.apiUrl}/organization`);
  }

  save(organization: Partial<Organization>): Observable<Organization> {
    return this.http.put<Organization>(`${this.env.apiUrl}/organization`, organization);
  }
}
