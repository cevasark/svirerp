import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { MembershipTypeService } from '../../services/membership-type.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { MembershipType } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MembershipTypeFormComponent } from '../membership-type-form/membership-type-form.component';

@Component({
  selector: 'app-membership-type-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Membership Types"
        subtitle="Tiers members can belong to, with annual fee and duration"
        actionLabel="Add Membership Type"
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
export class MembershipTypeListComponent implements OnInit {
  private typeService = inject(MembershipTypeService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<MembershipType> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name' },
    { key: 'annualFee', header: 'Annual Fee', cell: t => `$${Number(t.annualFee ?? 0).toFixed(2)}`, type: 'number' },
    { key: 'durationMonths', header: 'Duration (months)', type: 'number' },
    { key: 'isActive', header: 'Active', cell: t => (t.isActive ? 'Yes' : 'No'), type: 'boolean' },
    { key: 'canVote', header: 'Can Vote', cell: t => (t.canVote ? 'Yes' : 'No'), type: 'boolean' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (t: MembershipType) => this.openForm(t) },
    { icon: 'delete', label: 'Delete', action: (t: MembershipType) => this.confirmDelete(t) },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  openForm(type?: MembershipType): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(MembershipTypeFormComponent, {
        width: '540px',
        data: { orgId: this.orgId, type: type ?? null },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(type: MembershipType): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Membership Type',
          message: `Delete "${type.name}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteType(type); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.typeService.getPageForOrg(orgId, this.pageParams()).subscribe({
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

  private deleteType(type: MembershipType): void {
    this.typeService.remove(type.id).subscribe({
      next: () => {
        this.notifications.success('Membership type deleted.');
        this.loadPage();
      },
    });
  }
}
