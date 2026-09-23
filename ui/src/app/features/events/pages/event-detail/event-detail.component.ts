import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { EventService } from '../../services/event.service';
import { ChurchEventService } from '../../services/church-event.service';
import { EventRegistrationService } from '../../services/event-registration.service';
import { EventResourceService } from '../../services/event-resource.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { CalendarEvent, ChurchEvent, EventRegistration, EventResource } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { EventFormComponent } from '../event-form/event-form.component';
import { ChurchEventFormComponent } from '../church-event-form/church-event-form.component';
import { EventRegistrationFormComponent } from '../event-registration-form/event-registration-form.component';
import { EventResourceFormComponent } from '../event-resource-form/event-resource-form.component';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    @if (event(); as e) {
      <div class="page-container">
        <app-page-header [title]="e.title" [subtitle]="e.startDatetime + ' · ' + e.status">
          <ng-container extraActions>
            <button mat-stroked-button (click)="editEvent()">
              <mat-icon>edit</mat-icon>
              Edit
            </button>
          </ng-container>
        </app-page-header>

        @if (e.description) {
          <mat-card class="summary-card">
            <mat-card-content>{{ e.description }}</mat-card-content>
          </mat-card>
        }

        @if (e.googleOfficialSyncError || e.googleInternalSyncError) {
          <mat-card class="sync-error-card">
            <mat-card-content>
              <mat-icon>warning</mat-icon>
              <div>
                @if (e.googleOfficialSyncError) {
                  <p>Official Calendar: {{ e.googleOfficialSyncError }}</p>
                }
                @if (e.googleInternalSyncError) {
                  <p>Internal Calendar: {{ e.googleInternalSyncError }}</p>
                }
                <p class="sync-error-hint">Edit and save the event to retry.</p>
              </div>
            </mat-card-content>
          </mat-card>
        }

        <div class="section-header">
          <h2 class="mat-headline-6 section-title">Church Service Details</h2>
          <button mat-stroked-button (click)="openChurchForm()">
            <mat-icon>{{ churchEvent() ? 'edit' : 'add' }}</mat-icon>
            {{ churchEvent() ? 'Edit' : 'Add Church Details' }}
          </button>
        </div>
        @if (churchEvent(); as c) {
          <mat-card class="church-card">
            <mat-card-content>
              @if (c.serviceType) { <p><strong>Service Type:</strong> {{ c.serviceType }}</p> }
              @if (c.officiant) { <p><strong>Officiant:</strong> {{ c.officiant }}</p> }
              @if (c.sermonTitle) { <p><strong>Sermon:</strong> {{ c.sermonTitle }}</p> }
              @if (c.attendanceCount) { <p><strong>Attendance:</strong> {{ c.attendanceCount }}</p> }
            </mat-card-content>
          </mat-card>
        } @else {
          <p class="empty-hint">No church service details recorded yet.</p>
        }

        <div class="section-header">
          <h2 class="mat-headline-6 section-title">Registrations</h2>
          <button mat-flat-button color="primary" (click)="openRegistrationForm()">
            <mat-icon>person_add</mat-icon>
            Add Registration
          </button>
        </div>
        <app-data-table
          [columns]="registrationColumns"
          [actions]="registrationActions"
          [data]="registrations()"
          [loading]="registrationsLoading()"
          [pageParams]="registrationPageParams()"
          (pageChange)="onRegistrationPageChange($event)"
          (sortChange)="onRegistrationSortChange($event)" />

        <div class="section-header">
          <h2 class="mat-headline-6 section-title">Resources</h2>
          <button mat-flat-button color="primary" (click)="openResourceForm()">
            <mat-icon>add</mat-icon>
            Add Resource
          </button>
        </div>
        <app-data-table
          [columns]="resourceColumns"
          [actions]="resourceActions"
          [data]="resources()"
          [loading]="resourcesLoading()"
          [pageParams]="resourcePageParams()"
          (pageChange)="onResourcePageChange($event)" />
      </div>
    }
  `,
  styles: [`
    .summary-card, .church-card { margin-bottom: 16px; white-space: pre-wrap; }
    .sync-error-card { margin-bottom: 16px; background: #fff8e1; }
    .sync-error-card mat-card-content { display: flex; gap: 12px; align-items: flex-start; }
    .sync-error-card mat-icon { color: #f57c00; }
    .sync-error-card p { margin: 0 0 4px; }
    .sync-error-hint { color: rgba(0,0,0,.54); font-size: 0.85em; }
    .section-header { display: flex; align-items: center; justify-content: space-between; margin: 16px 0 8px; }
    .section-title { margin: 0; }
    .empty-hint { color: rgba(0,0,0,.54); margin: 0 0 16px; }
  `],
})
export class EventDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private eventService = inject(EventService);
  private churchEventService = inject(ChurchEventService);
  private registrationService = inject(EventRegistrationService);
  private resourceService = inject(EventResourceService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private eventId = this.route.snapshot.paramMap.get('id')!;

  event = signal<CalendarEvent | null>(null);
  churchEvent = signal<ChurchEvent | null>(null);

  registrations = signal<Page<EventRegistration> | null>(null);
  registrationsLoading = signal(false);
  registrationPageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  resources = signal<Page<EventResource> | null>(null);
  resourcesLoading = signal(false);
  resourcePageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly registrationColumns: TableColumn[] = [
    { key: 'name', header: 'Person', cell: (r: EventRegistration) => `${r.person.firstName} ${r.person.lastName}` },
    { key: 'email', header: 'Email', cell: (r: EventRegistration) => r.person.email, sortable: true, sortKey: 'person.email' },
    { key: 'status', header: 'Status' },
    { key: 'feePaid', header: 'Fee Paid', cell: (r: EventRegistration) => `$${Number(r.feePaid ?? 0).toFixed(2)}` },
    { key: 'ticketNumber', header: 'Ticket #', cell: (r: EventRegistration) => r.ticketNumber || '—' },
  ];

  readonly registrationActions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (r: EventRegistration) => this.openRegistrationForm(r) },
    { icon: 'delete', label: 'Delete', action: (r: EventRegistration) => this.confirmDeleteRegistration(r) },
  ];

  readonly resourceColumns: TableColumn[] = [
    { key: 'resourceType', header: 'Type' },
    { key: 'resourceName', header: 'Name' },
    { key: 'assignedTo', header: 'Assigned To', cell: (r: EventResource) => r.assignedTo || '—' },
    { key: 'notes', header: 'Notes', cell: (r: EventResource) => r.notes || '—' },
  ];

  readonly resourceActions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (r: EventResource) => this.openResourceForm(r) },
    { icon: 'delete', label: 'Delete', action: (r: EventResource) => this.confirmDeleteResource(r) },
  ];

  ngOnInit(): void {
    this.loadEvent();
    this.loadChurchEvent();
    this.loadRegistrations();
    this.loadResources();
  }

  onRegistrationPageChange(pageEvent: PageEvent): void {
    this.registrationPageParams.set({ ...this.registrationPageParams(), page: pageEvent.pageIndex, size: pageEvent.pageSize });
    this.loadRegistrations();
  }

  onRegistrationSortChange(sort: string | null): void {
    this.registrationPageParams.set({ ...this.registrationPageParams(), page: 0, sort: sort ?? undefined });
    this.loadRegistrations();
  }

  onResourcePageChange(pageEvent: PageEvent): void {
    this.resourcePageParams.set({ ...this.resourcePageParams(), page: pageEvent.pageIndex, size: pageEvent.pageSize });
    this.loadResources();
  }

  editEvent(): void {
    const e = this.event();
    if (!e) return;
    this.dialog
      .open(EventFormComponent, { width: '560px', data: { entity: e } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadEvent(); });
  }

  openChurchForm(): void {
    this.dialog
      .open(ChurchEventFormComponent, { width: '480px', data: { calendarEventId: this.eventId, entity: this.churchEvent() } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadChurchEvent(); });
  }

  openRegistrationForm(entity?: EventRegistration): void {
    this.dialog
      .open(EventRegistrationFormComponent, { width: '460px', data: { eventId: this.eventId, entity: entity ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadRegistrations(); });
  }

  confirmDeleteRegistration(registration: EventRegistration): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Registration',
          message: `Remove ${registration.person.firstName} ${registration.person.lastName}'s registration? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteRegistration(registration); });
  }

  openResourceForm(entity?: EventResource): void {
    this.dialog
      .open(EventResourceFormComponent, { width: '460px', data: { eventId: this.eventId, entity: entity ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadResources(); });
  }

  confirmDeleteResource(resource: EventResource): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Resource',
          message: `Delete "${resource.resourceName}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteResource(resource); });
  }

  private loadEvent(): void {
    this.eventService.getById(this.eventId).subscribe(event => this.event.set(event));
  }

  private loadChurchEvent(): void {
    this.churchEventService.getForEvent(this.eventId).subscribe(churchEvent => this.churchEvent.set(churchEvent));
  }

  private loadRegistrations(): void {
    this.registrationsLoading.set(true);
    this.registrationService.getForEvent(this.eventId, this.registrationPageParams()).subscribe({
      next: data => { this.registrations.set(data); this.registrationsLoading.set(false); },
      error: ()   => this.registrationsLoading.set(false),
    });
  }

  private loadResources(): void {
    this.resourcesLoading.set(true);
    this.resourceService.getForEvent(this.eventId, this.resourcePageParams()).subscribe({
      next: data => { this.resources.set(data); this.resourcesLoading.set(false); },
      error: ()   => this.resourcesLoading.set(false),
    });
  }

  private deleteRegistration(registration: EventRegistration): void {
    this.registrationService.remove(registration.id).subscribe({
      next: () => {
        this.notifications.success('Registration removed.');
        this.loadRegistrations();
      },
    });
  }

  private deleteResource(resource: EventResource): void {
    this.resourceService.remove(resource.id).subscribe({
      next: () => {
        this.notifications.success('Resource deleted.');
        this.loadResources();
      },
    });
  }
}
