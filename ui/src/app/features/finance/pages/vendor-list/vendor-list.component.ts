import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';

import { VendorService } from '../../services/vendor.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Vendor } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { VendorFormComponent } from '../vendor-form/vendor-form.component';

@Component({
  selector: 'app-vendor-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Vendors"
        subtitle="Payees for expenses — insurance, utilities, contractors, suppliers"
        actionLabel="Add Vendor"
        actionIcon="add"
        (action)="openForm()" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No vendors yet."
        (pageChange)="onPageChange($event)" />
    </div>
  `,
})
export class VendorListComponent implements OnInit {
  private vendorService = inject(VendorService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<Vendor> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name' },
    { key: 'category', header: 'Category', cell: v => v.category || '—' },
    { key: 'contactName', header: 'Contact', cell: v => v.contactName || '—' },
    { key: 'phone', header: 'Phone', cell: v => v.phone || '—' },
    { key: 'isActive', header: 'Active', cell: v => (v.isActive ? 'Yes' : 'No'), type: 'boolean' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (v: Vendor) => this.openForm(v) },
    { icon: 'delete', label: 'Delete', action: (v: Vendor) => this.confirmDelete(v) },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  openForm(vendor?: Vendor): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(VendorFormComponent, { width: '560px', data: { orgId: this.orgId, vendor: vendor ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(vendor: Vendor): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Vendor',
          message: `Delete "${vendor.name}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteVendor(vendor); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.vendorService.getPageForOrg(orgId, this.pageParams()).subscribe({
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

  private deleteVendor(vendor: Vendor): void {
    this.vendorService.remove(vendor.id).subscribe({
      next: () => {
        this.notifications.success('Vendor deleted.');
        this.loadPage();
      },
    });
  }
}
