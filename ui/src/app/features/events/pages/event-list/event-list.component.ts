import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';

import { EventService } from '../../services/event.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { CalendarEvent } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { EventFormComponent } from '../event-form/event-form.component';

/** Short "is this on the calendars it should be" summary for the list column — full error text is on the edit form and detail page. */
function calendarSyncSummary(e: CalendarEvent): string {
  const parts: string[] = [];
  if (e.publishToOfficial) parts.push(e.googleOfficialSyncError ? 'Official ⚠' : 'Official ✓');
  if (e.publishToInternal) parts.push(e.googleInternalSyncError ? 'Internal ⚠' : 'Internal ✓');
  return parts.length ? parts.join(' · ') : '—';
}

@Component({
  selector: 'app-event-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <div class="page-container">
      <app-page-header
        title="Events"
        subtitle="Calendar events and registrations"
        actionLabel="Add Event"
        actionIcon="event"
        (action)="openForm()" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>From</mat-label>
          <input matInput type="date" [(ngModel)]="fromFilter" (change)="onFilterChange()" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>To</mat-label>
          <input matInput type="date" [(ngModel)]="toFilter" (change)="onFilterChange()" />
        </mat-form-field>
      </div>

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)" />
    </div>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 12px; margin-bottom: 8px; }
    .filter-field { width: 200px; }
  `],
})
export class EventListComponent implements OnInit {
  private eventService = inject(EventService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);
  private router = inject(Router);

  private orgId: string | null = null;
  page = signal<Page<CalendarEvent> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  fromFilter: string | null = null;
  toFilter: string | null = null;

  readonly columns: TableColumn[] = [
    { key: 'title', header: 'Title' },
    { key: 'eventType', header: 'Type', cell: (e: CalendarEvent) => e.eventType || '—' },
    { key: 'startDatetime', header: 'Start', sortable: true, type: 'date' },
    { key: 'location', header: 'Location', cell: (e: CalendarEvent) => e.location || '—' },
    { key: 'status', header: 'Status', type: 'status' },
    { key: 'calendarSync', header: 'Calendar Sync', cell: (e: CalendarEvent) => calendarSyncSummary(e) },
  ];

  readonly actions: TableAction[] = [
    { icon: 'open_in_new', label: 'View', action: (e: CalendarEvent) => this.openDetail(e) },
    { icon: 'edit', label: 'Edit', action: (e: CalendarEvent) => this.openForm(e) },
    { icon: 'delete', label: 'Delete', action: (e: CalendarEvent) => this.confirmDelete(e) },
  ];

  ngOnInit(): void {
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.loadPage();
      },
      error: err => this.notifications.error(err.message ?? 'Could not load organization.'),
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  onFilterChange(): void {
    this.pageParams.set({ ...this.pageParams(), page: 0 });
    this.loadPage();
  }

  onSortChange(sort: string | null): void {
    this.pageParams.set({ ...this.pageParams(), page: 0, sort: sort ?? undefined });
    this.loadPage();
  }

  openDetail(event: CalendarEvent): void {
    this.router.navigate(['/events', event.id]);
  }

  openForm(entity?: CalendarEvent): void {
    if (!this.orgId) return;
    this.dialog
      .open(EventFormComponent, { width: '560px', data: { orgId: this.orgId, entity: entity ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(event: CalendarEvent): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Event',
          message: `Delete "${event.title}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteEvent(event); });
  }

  private loadPage(): void {
    if (!this.orgId) return;
    this.loading.set(true);
    // Backend expects `from`/`to` as full ISO datetimes; date inputs give plain dates.
    const from = this.fromFilter ? `${this.fromFilter}T00:00:00Z` : null;
    const to = this.toFilter ? `${this.toFilter}T23:59:59Z` : null;
    this.eventService.getPageForOrg(this.orgId, this.pageParams(), from, to).subscribe({
      next: data => { this.page.set(data); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  private deleteEvent(event: CalendarEvent): void {
    this.eventService.remove(event.id).subscribe({
      next: () => {
        this.notifications.success('Event deleted.');
        this.loadPage();
      },
    });
  }
}
