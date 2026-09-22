import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { AccountService } from '../../services/account.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Account } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { AccountFormComponent } from '../account-form/account-form.component';

const TYPE_ORDER: Record<string, number> = { revenue: 0, expense: 1, asset: 2, liability: 3, equity: 4 };

@Component({
  selector: 'app-account-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Categories"
        subtitle="Income and expense categories (chart of accounts) used when recording transactions"
        actionLabel="Add Category"
        actionIcon="add"
        (action)="openForm()" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No categories yet."
        (pageChange)="onPageChange($event)" />
    </div>
  `,
})
export class AccountListComponent implements OnInit {
  private accountService = inject(AccountService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<Account> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>({ ...DEFAULT_PAGE_PARAMS, size: 50 });

  readonly columns: TableColumn[] = [
    { key: 'accountNumber', header: '#', type: 'number' },
    { key: 'accountName', header: 'Name' },
    { key: 'accountType', header: 'Type', cell: a => a.accountType },
    { key: 'isActive', header: 'Active', cell: a => (a.isActive ? 'Yes' : 'No'), type: 'boolean' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Edit', disabled: (a: Account) => !!a.isSystem, action: (a: Account) => this.openForm(a) },
    { icon: 'delete', label: 'Delete', disabled: (a: Account) => !!a.isSystem, action: (a: Account) => this.confirmDelete(a) },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  openForm(account?: Account): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(AccountFormComponent, { width: '540px', data: { orgId: this.orgId, account: account ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(account: Account): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Category',
          message: `Delete "${account.accountName}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteAccount(account); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.accountService.getPageForOrg(orgId, this.pageParams()).subscribe({
          next: data => {
            data.content = [...data.content].sort((a, b) =>
              (TYPE_ORDER[a.accountType] - TYPE_ORDER[b.accountType]) || a.accountNumber.localeCompare(b.accountNumber),
            );
            this.page.set(data);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => {
        this.loading.set(false);
        this.notifications.error('No organization found — create one first, under Organizations.');
      },
    });
  }

  private deleteAccount(account: Account): void {
    this.accountService.remove(account.id).subscribe({
      next: () => {
        this.notifications.success('Category deleted.');
        this.loadPage();
      },
    });
  }
}
