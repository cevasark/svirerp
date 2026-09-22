import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';

import { VolunteerService } from '../../services/volunteer.service';
import { VolunteerAreaService } from '../../services/volunteer-area.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Person, Volunteer, VolunteerArea } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { VolunteerFormComponent } from '../volunteer-form/volunteer-form.component';
import { PersonDetailsDialogComponent } from '../../../persons/pages/person-details/person-details-dialog.component';

@Component({
  selector: 'app-volunteer-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatFormFieldModule, MatSelectModule, FormsModule],
  template: `
    <div class="page-container">
      <app-page-header
        title="Volunteers"
        subtitle="Volunteer directory, contacts and service areas"
        actionLabel="Add Volunteer"
        actionIcon="volunteer_activism"
        (action)="openForm()" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Area</mat-label>
          <mat-select [(ngModel)]="areaFilter" (selectionChange)="onFilterChange()">
            <mat-option [value]="null">All</mat-option>
            @for (a of areas(); track a.id) {
              <mat-option [value]="a.id">{{ a.name }}</mat-option>
            }
          </mat-select>
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
    .filter-field { width: 220px; }
  `],
})
export class VolunteerListComponent implements OnInit {
  private volunteerService = inject(VolunteerService);
  private areaService = inject(VolunteerAreaService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;

  page = signal<Page<Volunteer> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);
  areas = signal<VolunteerArea[]>([]);
  areaFilter: string | null = null;

  readonly columns: TableColumn[] = [
    {
      key: 'volunteer',
      header: 'Volunteer',
      cell: (v: Volunteer) => `${v.person.firstName} ${v.person.lastName}`,
      link: (v: Volunteer) => this.openPersonDetails(v.person),
    },
    { key: 'contact', header: 'Contact', cell: (v: Volunteer) => v.contactPerson ? `${v.contactPerson.firstName} ${v.contactPerson.lastName}` : '—' },
    { key: 'areas', header: 'Areas', cell: (v: Volunteer) => (v.areas ?? []).map(a => a.name).join(', ') || '—' },
    { key: 'onboardDate', header: 'Onboarded', sortable: true, type: 'date' },
    { key: 'isActive', header: 'Active', cell: (v: Volunteer) => v.isActive ? 'Yes' : 'No', type: 'boolean' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit',   label: 'Edit',   action: (v: Volunteer) => this.openForm(v) },
    { icon: 'delete', label: 'Delete', action: (v: Volunteer) => this.confirmDelete(v) },
  ];

  ngOnInit(): void {
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.loadPage();
        this.areaService.getPageForOrg(orgId, { page: 0, size: 100 }).subscribe(page => {
          this.areas.set(page.content);
        });
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

  openPersonDetails(person: Person): void {
    this.dialog.open(PersonDetailsDialogComponent, { width: '360px', data: person });
  }

  openForm(volunteer?: Volunteer): void {
    if (!this.orgId) return;
    this.dialog
      .open(VolunteerFormComponent, { width: '560px', data: { orgId: this.orgId, entity: volunteer ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(volunteer: Volunteer): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Volunteer',
          message: `Delete ${volunteer.person.firstName} ${volunteer.person.lastName} from the volunteer roster? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteVolunteer(volunteer); });
  }

  private loadPage(): void {
    if (!this.orgId) return;
    this.loading.set(true);
    this.volunteerService.getPageForOrg(this.orgId, this.pageParams(), this.areaFilter).subscribe({
      next: data => { this.page.set(data); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  private deleteVolunteer(volunteer: Volunteer): void {
    this.volunteerService.remove(volunteer.id).subscribe({
      next: () => {
        this.notifications.success('Volunteer deleted.');
        this.loadPage();
      },
    });
  }
}
