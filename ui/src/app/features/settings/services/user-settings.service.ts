import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { AppUserSummary } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class UserSettingsService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  listUsers(): Observable<AppUserSummary[]> {
    return this.http.get<AppUserSummary[]>(`${this.env.apiUrl}/settings/users`);
  }
}
