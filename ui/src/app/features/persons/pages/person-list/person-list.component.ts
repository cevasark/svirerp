import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { PersonService } from '../../services/person.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Person } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { PersonFormComponent } from '../person-form/person-form.component';
import { PersonImportDialogComponent } from '../person-import-dialog/person-import-dialog.component';

@Component({
  selector: 'app-person-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatButtonModule, MatIconModule],
  template: `
    <div class="page-container">
      <app-page-header
        title="People"
        subtitle="People and contacts in the system"
        actionLabel="Add Person"
        actionIcon="person_add"
        (action)="openForm()">
        <ng-container extraActions>
          <button mat-stroked-button (click)="downloadTemplate()">
            <mat-icon>download</mat-icon>
            Download Template
          </button>
          <button mat-stroked-button (click)="openImportDialog()">
            <mat-icon>upload</mat-icon>
            Import People
          </button>
        </ng-container>
      </app-page-header>

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
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<Person> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'firstName', header: 'First Name' },
    { key: 'lastName',  header: 'Last Name' },
    { key: 'email',     header: 'Email', sortable: true, type: 'email' },
    { key: 'phone',     header: 'Phone' },
    { key: 'city',      header: 'City' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit',   label: 'Edit',   action: (p: Person) => this.openForm(p) },
    { icon: 'delete', label: 'Delete', action: (p: Person) => this.confirmDelete(p) },
  ];

  ngOnInit(): void {
    this.loadPage();
    this.orgContext.ensureOrgId().subscribe(orgId => { this.orgId = orgId; });
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

  downloadTemplate(): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.personService.downloadImportTemplate(this.orgId).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'people-import-template.csv';
        anchor.click();
        URL.revokeObjectURL(url);
      },
    });
  }

  openImportDialog(): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(PersonImportDialogComponent, { width: '600px', data: { orgId: this.orgId } })
      .afterClosed()
      .subscribe(imported => { if (imported) this.loadPage(); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.personService.getPage(this.pageParams()).subscribe({
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
