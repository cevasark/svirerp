import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';

import { FinanceTransactionService } from '../../services/finance-transaction.service';
import { FundService } from '../../services/fund.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Fund, JournalEntry } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { IncomeFormComponent } from '../income-form/income-form.component';
import { ExpenseFormComponent } from '../expense-form/expense-form.component';
import { PayoutFormComponent } from '../payout-form/payout-form.component';
import { JournalEntryDetailDialogComponent } from '../journal-entry-detail-dialog/journal-entry-detail-dialog.component';

function partyName(entry: JournalEntry): string {
  if (entry.payer) return `${entry.payer.firstName} ${entry.payer.lastName}`;
  if (entry.vendor) return entry.vendor.name;
  return '—';
}

const PAYMENT_METHOD_OPTIONS: { value: string; label: string }[] = [
  { value: 'zeffy', label: 'Zeffy' },
  { value: 'stripe', label: 'Stripe' },
  { value: 'facebook', label: 'Facebook' },
  { value: 'cash', label: 'Cash' },
  { value: 'zelle', label: 'Zelle' },
  { value: 'check', label: 'Check' },
  { value: 'bank_transfer', label: 'Bank Transfer' },
  { value: 'card', label: 'Card' },
  { value: 'other', label: 'Other' },
];

@Component({
  selector: 'app-transaction-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, FormsModule],
  template: `
    <div class="page-container">
      <app-page-header
        title="Transactions"
        subtitle="Every dollar in and out — donations, dues, fundraising, bills and purchases"
        actionLabel="Record Income"
        actionIcon="add_circle"
        (action)="openIncomeForm()">
        <ng-container extraActions>
          <button mat-stroked-button (click)="openExpenseForm()">
            <mat-icon>remove_circle</mat-icon>
            Record Expense
          </button>
          <button mat-stroked-button (click)="openPayoutForm()">
            <mat-icon>sync_alt</mat-icon>
            Record Payout
          </button>
        </ng-container>
      </app-page-header>

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Project / Fund</mat-label>
          <mat-select [(ngModel)]="fundFilter" (selectionChange)="onFilterChange()">
            <mat-option [value]="null">All</mat-option>
            @for (f of funds(); track f.id) {
              <mat-option [value]="f.id">{{ f.fundName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Source / Method</mat-label>
          <mat-select [(ngModel)]="paymentMethodFilter" (selectionChange)="onFilterChange()">
            <mat-option [value]="null">All</mat-option>
            @for (m of paymentMethodOptions; track m.value) {
              <mat-option [value]="m.value">{{ m.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No transactions yet."
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)" />
    </div>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 12px; margin-bottom: 8px; }
    .filter-field { width: 240px; }
  `],
})
export class TransactionListComponent implements OnInit {
  private transactionService = inject(FinanceTransactionService);
  private fundService = inject(FundService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<JournalEntry> | null>(null);
  loading = signal(false);
  // Latest transactions first by default — treasurers care most about what just happened.
  pageParams = signal<PageParams>({ ...DEFAULT_PAGE_PARAMS, sort: 'entryDate,desc' });
  funds = signal<Fund[]>([]);
  fundFilter: string | null = null;
  paymentMethodFilter: string | null = null;
  readonly paymentMethodOptions = PAYMENT_METHOD_OPTIONS;

  readonly columns: TableColumn[] = [
    { key: 'entryDate', header: 'Date', sortable: true, type: 'date' },
    { key: 'description', header: 'Description', cell: e => e.description || '—' },
    { key: 'categoryAccount', header: 'Category', cell: e => e.categoryAccount?.accountName ?? '—' },
    { key: 'fund', header: 'Project / Fund', cell: e => e.fund?.fundName ?? '—' },
    { key: 'party', header: 'Payer / Payee', cell: partyName },
    { key: 'paymentMethod', header: 'Method', cell: e => e.paymentMethod ?? '—' },
    {
      key: 'totalDebit',
      header: 'Amount',
      cell: e => `${e.categoryAccount?.accountType === 'expense' ? '-' : '+'}${e.totalDebit.toFixed(2)}`,
      type: 'number',
    },
    { key: 'status', header: 'Status', type: 'status' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'receipt_long', label: 'View Detail', action: (e: JournalEntry) => this.openDetail(e) },
  ];

  ngOnInit(): void {
    this.loadPage();
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

  openIncomeForm(): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(IncomeFormComponent, { width: '560px', data: { orgId: this.orgId } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  openPayoutForm(): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(PayoutFormComponent, { width: '480px', data: { orgId: this.orgId } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  openDetail(entry: JournalEntry): void {
    this.dialog.open(JournalEntryDetailDialogComponent, { width: '560px', data: { entry } });
  }

  openExpenseForm(): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(ExpenseFormComponent, { width: '560px', data: { orgId: this.orgId } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        if (!this.funds().length) {
          this.fundService.getPageForOrg(orgId, { page: 0, size: 100 }).subscribe(page => {
            this.funds.set(page.content);
          });
        }
        this.transactionService
          .getPageForOrg(orgId, this.pageParams(), {
            fundId: this.fundFilter ?? undefined,
            paymentMethod: this.paymentMethodFilter ?? undefined,
          })
          .subscribe({
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
}
