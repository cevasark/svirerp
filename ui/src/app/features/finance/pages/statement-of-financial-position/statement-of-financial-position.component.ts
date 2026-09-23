import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FinanceReportService } from '../../services/finance-report.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { StatementOfFinancialPosition } from '../../../../core/models/domain.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

interface DisplayRow {
  label: string;
  amount: number;
  computed?: boolean;
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-statement-of-financial-position',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    DecimalPipe,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Statement of Financial Position"
        subtitle="Every account's balance as of a date — where does our money sit right now" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>As Of</mat-label>
          <input matInput type="date" [(ngModel)]="asOf" (change)="load()" />
        </mat-form-field>
      </div>

      @if (loading()) {
        <div class="loading"><mat-progress-spinner diameter="32" mode="indeterminate" /></div>
      } @else if (report(); as r) {
        <h3>Assets</h3>
        <table mat-table [dataSource]="assetRows()" class="report-table">
          <ng-container matColumnDef="label">
            <th mat-header-cell *matHeaderCellDef>Account</th>
            <td mat-cell *matCellDef="let row">{{ row.label }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Balance</th>
            <td mat-cell *matCellDef="let row">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!r.assets.length) {
          <p class="no-data">No asset accounts with activity.</p>
        }

        <h3>Liabilities</h3>
        <table mat-table [dataSource]="liabilityRows()" class="report-table">
          <ng-container matColumnDef="label">
            <th mat-header-cell *matHeaderCellDef>Account</th>
            <td mat-cell *matCellDef="let row">{{ row.label }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Balance</th>
            <td mat-cell *matCellDef="let row">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!r.liabilities.length) {
          <p class="no-data">No liability accounts with activity.</p>
        }

        <h3>Equity</h3>
        <table mat-table [dataSource]="equityRows()" class="report-table">
          <ng-container matColumnDef="label">
            <th mat-header-cell *matHeaderCellDef>Account</th>
            <td mat-cell *matCellDef="let row">{{ row.label }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Balance</th>
            <td mat-cell *matCellDef="let row">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"
              [class.computed-row]="row.computed"></tr>
        </table>
        <p class="hint">
          "Net Income (to date)" is calculated live from income and expense, not stored — this app
          doesn't run period-closing entries, so it's the only way Equity reflects income earned so far.
        </p>

        <dl class="summary-grid">
          <dt>Total Assets</dt>
          <dd>{{ r.totalAssets | number: '1.2-2' }}</dd>
          <dt>Total Liabilities</dt>
          <dd>{{ r.totalLiabilities | number: '1.2-2' }}</dd>
          <dt>Total Equity</dt>
          <dd>{{ r.totalEquity | number: '1.2-2' }}</dd>
          <dt class="total">Total Liabilities &amp; Equity</dt>
          <dd class="total">{{ r.totalLiabilitiesAndEquity | number: '1.2-2' }}</dd>
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
    .computed-row { font-style: italic; color: rgba(0,0,0,.7); }
    .hint { color: rgba(0,0,0,.54); font-size: .85em; max-width: 560px; margin: 8px 0 20px; }
    .summary-grid {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 8px 24px;
      max-width: 360px;
      margin: 8px 0 0;
    }
    .summary-grid dt { color: rgba(0,0,0,.6); }
    .summary-grid dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
    .total { font-weight: 600; border-top: 1px solid rgba(0,0,0,.12); padding-top: 8px; }
  `],
})
export class StatementOfFinancialPositionComponent implements OnInit {
  private reportService = inject(FinanceReportService);
  private notifications = inject(NotificationService);

  readonly columns = ['label', 'amount'];

  asOf = toIsoDate(new Date());

  loading = signal(false);
  report = signal<StatementOfFinancialPosition | null>(null);

  assetRows = signal<DisplayRow[]>([]);
  liabilityRows = signal<DisplayRow[]>([]);
  equityRows = signal<DisplayRow[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.reportService.statementOfFinancialPosition(this.asOf).subscribe({
      next: r => {
        this.report.set(r);
        this.assetRows.set(r.assets.map(l => ({ label: `${l.accountNumber} — ${l.accountName}`, amount: l.balance })));
        this.liabilityRows.set(r.liabilities.map(l => ({ label: `${l.accountNumber} — ${l.accountName}`, amount: l.balance })));
        this.equityRows.set([
          ...r.equity.map(l => ({ label: `${l.accountNumber} — ${l.accountName}`, amount: l.balance })),
          { label: 'Net Income (to date)', amount: r.netIncomeToDate, computed: true },
        ]);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
