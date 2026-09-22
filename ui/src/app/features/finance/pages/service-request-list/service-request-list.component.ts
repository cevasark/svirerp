import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { ServiceRequestService } from '../../services/service-request.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ServiceRequest } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ServiceRequestFormComponent } from '../service-request-form/service-request-form.component';
import { IncomeFormComponent } from '../income-form/income-form.component';

const SERVICE_TYPE_LABELS: Record<string, string> = {
  wedding: 'Wedding',
  baptism: 'Baptism',
  funeral: 'Funeral',
  memorial: 'Memorial',
  blessing: 'Blessing',
  other: 'Other',
};

@Component({
  selector: 'app-service-request-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Service Requests"
        subtitle="Pre-paid church services — weddings, baptisms, funerals, memorials"
        actionLabel="Add Service Request"
        actionIcon="add"
        (action)="openForm()" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No service requests yet."
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)" />
    </div>
  `,
})
export class ServiceRequestListComponent implements OnInit {
  private serviceRequestService = inject(ServiceRequestService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<ServiceRequest> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);
  balances = signal<Record<string, number>>({});

  readonly columns: TableColumn[] = [
    { key: 'requestorPerson', header: 'Requested By', cell: r => r.requestorPerson ? `${r.requestorPerson.firstName} ${r.requestorPerson.lastName}` : '—' },
    { key: 'serviceType', header: 'Service', cell: r => SERVICE_TYPE_LABELS[r.serviceType] ?? r.serviceType },
    { key: 'requestedDate', header: 'Date', cell: r => r.requestedDate || '—', sortable: true, type: 'date' },
    { key: 'status', header: 'Status', cell: r => r.status, type: 'status' },
    { key: 'agreedAmount', header: 'Agreed', cell: r => r.agreedAmount.toFixed(2), type: 'number' },
    { key: 'balance', header: 'Balance Due', cell: r => (this.balances()[r.id] ?? r.agreedAmount).toFixed(2), type: 'number' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'payments', label: 'Record payment', disabled: (r: ServiceRequest) => (this.balances()[r.id] ?? r.agreedAmount) <= 0, action: (r: ServiceRequest) => this.openRecordPayment(r) },
    { icon: 'edit', label: 'Edit', action: (r: ServiceRequest) => this.openForm(r) },
    { icon: 'delete', label: 'Delete', action: (r: ServiceRequest) => this.confirmDelete(r) },
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

  openForm(request?: ServiceRequest): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(ServiceRequestFormComponent, { width: '560px', data: { orgId: this.orgId, request: request ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  openRecordPayment(request: ServiceRequest): void {
    if (!this.orgId) return;
    const balance = this.balances()[request.id] ?? request.agreedAmount;
    this.dialog
      .open(IncomeFormComponent, {
        width: '560px',
        data: { orgId: this.orgId, prefill: { serviceRequestId: request.id, amount: balance, description: `Payment for ${SERVICE_TYPE_LABELS[request.serviceType] ?? request.serviceType}` } },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(request: ServiceRequest): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Service Request',
          message: `Delete this ${SERVICE_TYPE_LABELS[request.serviceType] ?? request.serviceType} request? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteRequest(request); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.serviceRequestService.getPageForOrg(orgId, this.pageParams()).subscribe({
          next: data => {
            this.page.set(data);
            this.loading.set(false);
            this.loadBalances(data.content);
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

  private loadBalances(requests: ServiceRequest[]): void {
    if (!requests.length) return;
    forkJoin(
      requests.map(r =>
        this.serviceRequestService.balance(r.id).pipe(catchError(() => of(r.agreedAmount))),
      ),
    ).subscribe(values => {
      const next: Record<string, number> = {};
      requests.forEach((r, i) => { next[r.id] = values[i]; });
      this.balances.set(next);
    });
  }

  private deleteRequest(request: ServiceRequest): void {
    this.serviceRequestService.remove(request.id).subscribe({
      next: () => {
        this.notifications.success('Service request deleted.');
        this.loadPage();
      },
    });
  }
}
