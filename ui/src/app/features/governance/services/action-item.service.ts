import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { ActionItem } from '../../../core/models/domain.model';

@Injectable({ providedIn: 'root' })
export class ActionItemService extends ResourceService<ActionItem> {
  private readonly apiEnv = inject(ENVIRONMENT);

  constructor() {
    super('action-items');
  }

  /**
   * Deliberately unpaginated — a meeting's action items are meant to be seen
   * as a whole live list while trustees are discussing, not paged through.
   */
  getForMeeting(meetingMinutesId: string): Observable<ActionItem[]> {
    return this.http.get<ActionItem[]>(`${this.apiEnv.apiUrl}/meeting-minutes/${meetingMinutesId}/action-items`);
  }
}
