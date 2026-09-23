import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FinanceReportService } from '../../services/finance-report.service';
import { FundService } from '../../services/fund.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Fund, StatementOfActivities } from '../../../../core/models/domain.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

interface DisplayRow {
  label: string;
  amount: number;
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function firstOfMonth(date: Date): string {
  return toIsoDate(new Date(date.getFullYear(), date.getMonth(), 1));
}

@Component({
  selector: 'app-statement-of-activities',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    DecimalPipe,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Statement of Activities"
        subtitle="Income vs. expense for a period — how did this period go" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>From</mat-label>
          <input matInput type="date" [(ngModel)]="from" (change)="load()" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>To</mat-label>
          <input matInput type="date" [(ngModel)]="to" (change)="load()" />
        </mat-form-field>
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Project / Fund</mat-label>
          <mat-select [(ngModel)]="fundId" (selectionChange)="load()">
            <mat-option [value]="null">All</mat-option>
            @for (f of funds(); track f.id) {
              <mat-option [value]="f.id">{{ f.fundName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      @if (loading()) {
        <div class="loading"><mat-progress-spinner diameter="32" mode="indeterminate" /></div>
      } @else if (report(); as r) {
        <h3>Income</h3>
        <table mat-table [dataSource]="incomeRows()" class="report-table">
          <ng-container matColumnDef="label">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let row">{{ row.label }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let row">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!r.income.length) {
          <p class="no-data">No income in this period.</p>
        }

        <h3>Expense</h3>
        <table mat-table [dataSource]="expenseRows()" class="report-table">
          <ng-container matColumnDef="label">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let row">{{ row.label }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let row">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!r.expense.length) {
          <p class="no-data">No expense in this period.</p>
        }

        <dl class="summary-grid">
          <dt>Total Income</dt>
          <dd class="positive">{{ r.totalIncome | number: '1.2-2' }}</dd>
          <dt>Total Expense</dt>
          <dd class="negative">{{ r.totalExpense | number: '1.2-2' }}</dd>
          <dt class="total">Net Change</dt>
          <dd class="total">{{ r.netChange | number: '1.2-2' }}</dd>
        </dl>
      }
    </div>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
    .filter-field { width: 200px; }
    .loading { display: flex; justify-content: center; padding: 24px; }
    h3 { margin: 20px 0 4px; }
    .report-table { width: 100%; margin-bottom: 4px; }
    .no-data { color: rgba(0,0,0,.54); margin: 8px 0 20px; }
    .summary-grid {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 8px 24px;
      max-width: 360px;
      margin: 24px 0 0;
    }
    .summary-grid dt { color: rgba(0,0,0,.6); }
    .summary-grid dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
    .positive { color: #2e7d32; }
    .negative { color: #c62828; }
    .total { font-weight: 600; border-top: 1px solid rgba(0,0,0,.12); padding-top: 8px; }
  `],
})
export class StatementOfActivitiesComponent implements OnInit {
  private reportService = inject(FinanceReportService);
  private fundService = inject(FundService);
  private notifications = inject(NotificationService);

  readonly columns = ['label', 'amount'];

  from = firstOfMonth(new Date());
  to = toIsoDate(new Date());
  fundId: string | null = null;

  loading = signal(false);
  report = signal<StatementOfActivities | null>(null);
  funds = signal<Fund[]>([]);

  incomeRows = signal<DisplayRow[]>([]);
  expenseRows = signal<DisplayRow[]>([]);

  ngOnInit(): void {
    this.fundService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.funds.set(page.content);
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.reportService
      .statementOfActivities(this.from, this.to, this.fundId ?? undefined)
      .subscribe({
        next: r => {
          this.report.set(r);
          this.incomeRows.set(r.income.map(l => ({ label: `${l.accountNumber} — ${l.accountName}`, amount: l.amount })));
          this.expenseRows.set(r.expense.map(l => ({ label: `${l.accountNumber} — ${l.accountName}`, amount: l.amount })));
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
