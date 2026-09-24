import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { PersonService } from '../../services/person.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Person, PersonOverview } from '../../../../core/models/domain.model';
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
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="People"
        subtitle="People and contacts in the system"
        actionLabel="Add Person"
        actionIcon="person_add"
        (action)="openForm()" />

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
})
export class PersonListComponent implements OnInit {
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  page = signal<Page<PersonOverview> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'firstName', header: 'First Name' },
    { key: 'lastName',  header: 'Last Name' },
    { key: 'email',     header: 'Email', sortable: true, type: 'email' },
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
    this.personService.getOverviewPage(this.pageParams()).subscribe({
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
