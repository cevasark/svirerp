import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { FundService } from '../../services/fund.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Fund } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { FundFormComponent } from '../fund-form/fund-form.component';
import { FundSummaryDialogComponent } from '../fund-summary-dialog/fund-summary-dialog.component';

@Component({
  selector: 'app-fund-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Projects &amp; Funds"
        subtitle="Restricted and unrestricted funds — a project's financial status is its fund balance"
        actionLabel="Add Project / Fund"
        actionIcon="add"
        (action)="openForm()" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        (pageChange)="onPageChange($event)" />
    </div>
  `,
})
export class FundListComponent implements OnInit {
  private fundService = inject(FundService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<Fund> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'fundName', header: 'Name' },
    { key: 'fundCode', header: 'Code', type: 'number' },
    { key: 'fundType', header: 'Type', cell: f => f.fundType.replace(/_/g, ' ') },
    { key: 'isActive', header: 'Active', cell: f => (f.isActive ? 'Yes' : 'No'), type: 'boolean' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'summarize', label: 'Financial status', action: (f: Fund) => this.openSummary(f) },
    { icon: 'edit', label: 'Edit', action: (f: Fund) => this.openForm(f) },
    { icon: 'delete', label: 'Delete', action: (f: Fund) => this.confirmDelete(f) },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  openForm(fund?: Fund): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(FundFormComponent, { width: '540px', data: { orgId: this.orgId, fund: fund ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  openSummary(fund: Fund): void {
    this.dialog.open(FundSummaryDialogComponent, { width: '420px', data: { fund } });
  }

  confirmDelete(fund: Fund): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Project / Fund',
          message: `Delete "${fund.fundName}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteFund(fund); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.fundService.getPageForOrg(orgId, this.pageParams()).subscribe({
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

  private deleteFund(fund: Fund): void {
    this.fundService.remove(fund.id).subscribe({
      next: () => {
        this.notifications.success('Fund deleted.');
        this.loadPage();
      },
    });
  }
}
