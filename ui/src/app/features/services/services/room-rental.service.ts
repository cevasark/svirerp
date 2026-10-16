import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { RoomRental } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class RoomRentalService extends ResourceService<RoomRental> {
  constructor() {
    super('room-rentals');
  }

  getFilteredPage(status?: string, params: PageParams = DEFAULT_PAGE_PARAMS): Observable<Page<RoomRental>> {
    let p = new HttpParams()
      .set('page', String(params.page))
      .set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (status) {
      p = p.set('status', status);
    }
    return this.http.get<Page<RoomRental>>(this.endpoint(), { params: p });
  }

  getSchedule(start: string, end: string, roomName?: string): Observable<RoomRental[]> {
    let p = new HttpParams()
      .set('start', start)
      .set('end', end);
    if (roomName) {
      p = p.set('roomName', roomName);
    }
    return this.http.get<RoomRental[]>(this.endpoint('/schedule'), { params: p });
  }

  checkOverlap(start: string, end: string, roomName = 'Parish Hall', excludeId?: string): Observable<RoomRental[]> {
    let p = new HttpParams()
      .set('start', start)
      .set('end', end)
      .set('roomName', roomName);
    if (excludeId) {
      p = p.set('excludeId', excludeId);
    }
    return this.http.get<RoomRental[]>(this.endpoint('/check-overlap'), { params: p });
  }
}
