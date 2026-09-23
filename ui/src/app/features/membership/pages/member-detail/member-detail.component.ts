import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { MemberService } from '../../services/member.service';
import { MemberPaymentService } from '../../services/member-payment.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Member, MemberPayment } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MemberFormComponent } from '../member-form/member-form.component';
import { MemberPaymentFormComponent } from '../member-payment-form/member-payment-form.component';

@Component({
  selector: 'app-member-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatCardModule, MatButtonModule, MatIconModule],
  template: `
    @if (member(); as m) {
      <div class="page-container">
        <app-page-header
          [title]="m.person.firstName + ' ' + m.person.lastName"
          [subtitle]="m.membershipType.name + ' · ' + m.status + ' · joined ' + m.joinDate">
          <ng-container extraActions>
            <button mat-stroked-button (click)="editMember()">
              <mat-icon>edit</mat-icon>
              Edit
            </button>
          </ng-container>
        </app-page-header>

        <mat-card class="contact-card">
          <mat-card-content>{{ m.person.email }}@if (m.person.phone) { &nbsp;·&nbsp; {{ m.person.phone }} }</mat-card-content>
        </mat-card>

        <div class="section-header">
          <h2 class="mat-headline-6 section-title">Contributions</h2>
          <button mat-flat-button color="primary" (click)="openPaymentForm()">
            <mat-icon>volunteer_activism</mat-icon>
            Add Contribution
          </button>
        </div>

        <app-data-table
          [columns]="paymentColumns"
          [actions]="paymentActions"
          [data]="payments()"
          [loading]="paymentsLoading()"
          [pageParams]="paymentPageParams()"
          (pageChange)="onPaymentPageChange($event)"
          (sortChange)="onPaymentSortChange($event)" />
      </div>
    }
  `,
  styles: [`
    .contact-card { margin-bottom: 24px; }
    .section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
    .section-title { margin: 0; }
  `],
})
export class MemberDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private memberService = inject(MemberService);
  private paymentService = inject(MemberPaymentService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private memberId = this.route.snapshot.paramMap.get('id')!;

  member = signal<Member | null>(null);
  payments = signal<Page<MemberPayment> | null>(null);
  paymentsLoading = signal(false);
  paymentPageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly paymentColumns: TableColumn[] = [
    { key: 'amount', header: 'Amount', cell: (p: MemberPayment) => `$${Number(p.amount).toFixed(2)}` },
    { key: 'paymentDate', header: 'Date', sortable: true },
    { key: 'notes', header: 'Notes', cell: (p: MemberPayment) => p.notes || '—' },
  ];

  readonly paymentActions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (p: MemberPayment) => this.openPaymentForm(p) },
    { icon: 'delete', label: 'Delete', action: (p: MemberPayment) => this.confirmDeletePayment(p) },
  ];

  ngOnInit(): void {
    this.loadMember();
    this.loadPayments();
  }

  onPaymentPageChange(event: PageEvent): void {
    this.paymentPageParams.set({ ...this.paymentPageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPayments();
  }

  onPaymentSortChange(sort: string | null): void {
    this.paymentPageParams.set({ ...this.paymentPageParams(), page: 0, sort: sort ?? undefined });
    this.loadPayments();
  }

  editMember(): void {
    const m = this.member();
    if (!m) return;
    this.dialog
      .open(MemberFormComponent, { width: '540px', data: { member: m } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadMember(); });
  }

  openPaymentForm(entity?: MemberPayment): void {
    const m = this.member();
    if (!m) return;
    this.dialog
      .open(MemberPaymentFormComponent, { width: '480px', data: { member: m, entity: entity ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPayments(); });
  }

  confirmDeletePayment(payment: MemberPayment): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Contribution',
          message: `Delete this $${Number(payment.amount).toFixed(2)} contribution? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deletePayment(payment); });
  }

  private loadMember(): void {
    this.memberService.getById(this.memberId).subscribe(member => this.member.set(member));
  }

  private loadPayments(): void {
    this.paymentsLoading.set(true);
    this.paymentService.getForMember(this.memberId, this.paymentPageParams()).subscribe({
      next: data => { this.payments.set(data); this.paymentsLoading.set(false); },
      error: ()   => this.paymentsLoading.set(false),
    });
  }

  private deletePayment(payment: MemberPayment): void {
    this.paymentService.remove(payment.id).subscribe({
      next: () => {
        this.notifications.success('Contribution deleted.');
        this.loadPayments();
      },
    });
  }
}
