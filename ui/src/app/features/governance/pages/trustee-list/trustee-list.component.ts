import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { TrusteeService } from '../../services/trustee.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Trustee } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { TrusteeFormComponent } from '../trustee-form/trustee-form.component';

/**
 * Status has no DB column — it's derived here from term_end/is_active so a
 * trustee past their term but not explicitly deactivated keeps showing as
 * "serving" (Expired, not Inactive), matching the auto-renewal-in-practice
 * behavior: nothing forces them off the board until someone acts on it.
 */
function trusteeStatus(t: Trustee): 'Active' | 'Inactive' | 'Expired' {
  if (!t.isActive) return 'Inactive';
  if (t.termEnd && new Date(t.termEnd) < new Date()) return 'Expired';
  return 'Active';
}

@Component({
  selector: 'app-trustee-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Trustees"
        subtitle="Board trustees and officers"
        actionLabel="Add Trustee"
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
export class TrusteeListComponent implements OnInit {
  private trusteeService = inject(TrusteeService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<Trustee> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'person', header: 'Name', cell: t => `${t.person.firstName} ${t.person.lastName}` },
    { key: 'title', header: 'Title', cell: t => t.title || t.role },
    { key: 'termStart', header: 'Term Start', sortable: true, type: 'date' },
    { key: 'termEnd', header: 'Term End', cell: t => t.termEnd || '—', sortable: true, type: 'date' },
    { key: 'status', header: 'Status', cell: t => trusteeStatus(t), type: 'status' },
    { key: 'isOfficer', header: 'Officer', cell: t => (t.isOfficer ? 'Yes' : 'No'), type: 'boolean' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (t: Trustee) => this.openForm(t) },
    {
      icon: 'autorenew',
      label: 'Renew term',
      disabled: (t: Trustee) => trusteeStatus(t) !== 'Expired',
      action: (t: Trustee) => this.confirmRenew(t),
    },
    { icon: 'delete', label: 'Delete', action: (t: Trustee) => this.confirmDelete(t) },
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

  openForm(trustee?: Trustee): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(TrusteeFormComponent, {
        width: '540px',
        data: { orgId: this.orgId, trustee: trustee ?? null },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmRenew(trustee: Trustee): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Renew Term',
          message: `Renew ${trustee.person.firstName} ${trustee.person.lastName}'s term for another 2 years?`,
          confirmLabel: 'Renew',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.renewTrustee(trustee); });
  }

  confirmDelete(trustee: Trustee): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Trustee',
          message: `Delete ${trustee.person.firstName} ${trustee.person.lastName} as a trustee? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteTrustee(trustee); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.trusteeService.getPageForOrg(orgId, this.pageParams()).subscribe({
          next: data => { this.page.set(data); this.loading.set(false); },
          error: () => this.loading.set(false),
        });
      },
      error: () => {
        this.loading.set(false);
        this.notifications.error('No organization found — create one first, under Organizations.');
      },
    });
  }

  private renewTrustee(trustee: Trustee): void {
    this.trusteeService.renew(trustee.id).subscribe({
      next: () => {
        this.notifications.success('Trustee term renewed.');
        this.loadPage();
      },
    });
  }

  private deleteTrustee(trustee: Trustee): void {
    this.trusteeService.remove(trustee.id).subscribe({
      next: () => {
        this.notifications.success('Trustee deleted.');
        this.loadPage();
      },
    });
  }
}
