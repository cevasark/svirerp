import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';

import { MemberPaymentService } from '../../services/member-payment.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { MemberPayment } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MemberPaymentFormComponent } from '../member-payment-form/member-payment-form.component';

@Component({
  selector: 'app-member-payment-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatFormFieldModule, MatInputModule, FormsModule],
  template: `
    <div class="page-container">
      <app-page-header
        title="Contributions"
        subtitle="Member contributions across the organization"
        actionLabel="Add Contribution"
        actionIcon="volunteer_activism"
        (action)="openForm()" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>On or after</mat-label>
          <input matInput type="date" [(ngModel)]="fromDateFilter" (change)="onFilterChange()" />
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
    .filter-field { width: 200px; }
  `],
})
export class MemberPaymentListComponent implements OnInit {
  private paymentService = inject(MemberPaymentService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<MemberPayment> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  fromDateFilter: string | null = null;

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Member Name', cell: (p: MemberPayment) => `${p.member.person.firstName} ${p.member.person.lastName}` },
    { key: 'email', header: 'Member Email', cell: (p: MemberPayment) => p.member.person.email, sortable: true, sortKey: 'member.person.email', type: 'email' },
    { key: 'amount', header: 'Amount', cell: (p: MemberPayment) => `$${Number(p.amount).toFixed(2)}`, type: 'number' },
    { key: 'paymentDate', header: 'Date', sortable: true, type: 'date' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (p: MemberPayment) => this.openForm(p) },
    { icon: 'delete', label: 'Delete', action: (p: MemberPayment) => this.confirmDelete(p) },
  ];

  ngOnInit(): void {
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.loadPage();
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

  openForm(entity?: MemberPayment): void {
    if (!this.orgId) return;
    this.dialog
      .open(MemberPaymentFormComponent, { width: '480px', data: { orgId: this.orgId, member: null, entity: entity ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(payment: MemberPayment): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Contribution',
          message: `Delete this $${Number(payment.amount).toFixed(2)} contribution from ${payment.member.person.firstName} ${payment.member.person.lastName}? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deletePayment(payment); });
  }

  private loadPage(): void {
    if (!this.orgId) return;
    this.loading.set(true);
    this.paymentService.getPageForOrg(this.orgId, this.pageParams(), this.fromDateFilter).subscribe({
      next: data => { this.page.set(data); this.loading.set(false); },
      error: ()   => this.loading.set(false),
    });
  }

  private deletePayment(payment: MemberPayment): void {
    this.paymentService.remove(payment.id).subscribe({
      next: () => {
        this.notifications.success('Contribution deleted.');
        this.loadPage();
      },
    });
  }
}
