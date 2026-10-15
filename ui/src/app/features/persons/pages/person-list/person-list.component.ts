import { Component, inject, signal, computed, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';

import { PersonService, PersonFilter } from '../../services/person.service';
import { MembershipTypeService } from '../../../membership/services/membership-type.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Person, PersonOverview, MembershipType } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { PersonFormComponent } from '../person-form/person-form.component';
import { PersonDetailsDialogComponent } from '../person-details/person-details-dialog.component';

@Component({
  selector: 'app-person-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DataTableComponent,
    PageHeaderComponent,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    FormsModule,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="People"
        subtitle="People and contacts in the system"
        actionLabel="Add Person"
        actionIcon="person_add"
        (action)="openForm()" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field" subscriptSizing="dynamic">
          <mat-label>Name</mat-label>
          <input
            matInput
            [(ngModel)]="nameFilter"
            placeholder="First or last name (wildcard %)"
            (keyup.enter)="applyFilter()" />
          @if (nameFilter) {
            <button matSuffix mat-icon-button aria-label="Clear name" (click)="nameFilter = ''">
              <mat-icon>close</mat-icon>
            </button>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="filter-field" subscriptSizing="dynamic">
          <mat-label>Email</mat-label>
          <input
            matInput
            [(ngModel)]="emailFilter"
            placeholder="Email address (wildcard %)"
            (keyup.enter)="applyFilter()" />
          @if (emailFilter) {
            <button matSuffix mat-icon-button aria-label="Clear email" (click)="emailFilter = ''">
              <mat-icon>close</mat-icon>
            </button>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="filter-field" subscriptSizing="dynamic">
          <mat-label>Membership Type</mat-label>
          <mat-select [(ngModel)]="membershipTypeFilter">
            <mat-option [value]="null">All Types</mat-option>
            <mat-option value="none">None (No Membership)</mat-option>
            @for (t of membershipTypes(); track t.id) {
              <mat-option [value]="t.id">{{ t.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="filter-actions">
          <button mat-flat-button color="primary" (click)="applyFilter()" class="filter-btn">
            <mat-icon>filter_list</mat-icon>
            Filter
          </button>
          @if (hasActiveFilters()) {
            <button mat-stroked-button (click)="resetFilter()" class="reset-btn">
              <mat-icon>clear</mat-icon>
              Reset
            </button>
          }
        </div>
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
    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
    }
    .filter-field {
      width: 240px;
    }
    .filter-actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .filter-btn, .reset-btn {
      height: 48px;
    }
  `],
})
export class PersonListComponent implements OnInit {
  private personService = inject(PersonService);
  private membershipTypeService = inject(MembershipTypeService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  page = signal<Page<PersonOverview> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);
  membershipTypes = signal<MembershipType[]>([]);

  nameFilter = '';
  emailFilter = '';
  membershipTypeFilter: string | null = null;
  appliedFilter = signal<PersonFilter>({});

  hasActiveFilters = computed(() => {
    const f = this.appliedFilter();
    return !!(f.name || f.email || f.membershipTypeId);
  });

  readonly columns: TableColumn[] = [
    { key: 'firstName', header: 'First Name', sortable: true },
    { key: 'lastName',  header: 'Last Name',  sortable: true },
    { key: 'email',     header: 'Email',      sortable: true, type: 'email' },
    { key: 'phone',     header: 'Phone' },
    { key: 'membershipType', header: 'Membership', type: 'status',
      cell: (p: PersonOverview) => p.membershipType ? `${p.membershipType} (${p.membershipStatus})` : '—' },
    { key: 'zeffyStatus', header: 'Zeffy', type: 'status', cell: (p: PersonOverview) => this.zeffyStatus(p) },
    { key: 'zeffyContribution', header: 'Zeffy Total', type: 'number',
      cell: (p: PersonOverview) => this.zeffyContribution(p) },
  ];

  readonly actions: TableAction[] = [
    { icon: 'visibility', label: 'Details', action: (p: PersonOverview) => this.openDetails(p) },
    { icon: 'edit',   label: 'Edit',   action: (p: Person) => this.openForm(p) },
    { icon: 'delete', label: 'Delete', action: (p: Person) => this.confirmDelete(p) },
  ];

  ngOnInit(): void {
    this.membershipTypeService.getPage({ page: 0, size: 100 }).subscribe({
      next: page => this.membershipTypes.set(page.content),
      error: () => {},
    });
    this.loadPage();
  }

  applyFilter(): void {
    this.appliedFilter.set({
      name: this.nameFilter.trim() || undefined,
      email: this.emailFilter.trim() || undefined,
      membershipTypeId: this.membershipTypeFilter || undefined,
    });
    this.pageParams.set({ ...this.pageParams(), page: 0 });
    this.loadPage();
  }

  resetFilter(): void {
    this.nameFilter = '';
    this.emailFilter = '';
    this.membershipTypeFilter = null;
    this.appliedFilter.set({});
    this.pageParams.set({ ...this.pageParams(), page: 0 });
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  onSortChange(sort: string | null): void {
    this.pageParams.set({ ...this.pageParams(), page: 0, sort: sort ?? undefined });
    this.loadPage();
  }

  openForm(person?: Person): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: person ?? null })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  openDetails(person: PersonOverview): void {
    this.dialog.open(PersonDetailsDialogComponent, { width: '480px', data: person });
  }

  zeffyStatus(person: PersonOverview): string {
    if (!person.zeffyContacts.length) return 'Not linked';
    if (person.zeffyContacts.some(contact => contact.processingStatus === 'NEEDS_REVIEW')) return 'Needs review';
    if (person.zeffyContacts.every(contact => contact.processingStatus === 'DELETED')) return 'Deleted in Zeffy';
    return 'Linked';
  }

  zeffyContribution(person: PersonOverview): string {
    if (!person.zeffyContacts.length) return '—';
    const total = person.zeffyContacts.reduce((sum, contact) => sum + (contact.totalContribution ?? 0), 0);
    const currency = person.zeffyContacts.find(contact => contact.currency)?.currency ?? 'USD';
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(total);
  }

  confirmDelete(person: Person): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Person',
          message: `Delete ${person.firstName} ${person.lastName}? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deletePerson(person); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.personService.getOverviewPage(this.pageParams(), this.appliedFilter()).subscribe({
      next: data => { this.page.set(data); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  private deletePerson(person: Person): void {
    this.personService.remove(person.id).subscribe({
      next: () => {
        this.notifications.success('Person deleted.');
        this.loadPage();
      },
    });
  }
}
