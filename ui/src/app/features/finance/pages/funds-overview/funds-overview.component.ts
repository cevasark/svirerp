import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FinanceReportService } from '../../services/finance-report.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { FundOverviewRow } from '../../../../core/models/domain.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-funds-overview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatTableModule, MatProgressSpinnerModule, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Funds Overview"
        subtitle="Every fund's opening balance, income, expense and balance in one place" />

      @if (loading()) {
        <div class="loading"><mat-progress-spinner diameter="32" mode="indeterminate" /></div>
      } @else {
        <table mat-table [dataSource]="rows()" class="report-table">
          <ng-container matColumnDef="fundName">
            <th mat-header-cell *matHeaderCellDef>Fund</th>
            <td mat-cell *matCellDef="let row">{{ row.fundName }}</td>
          </ng-container>
          <ng-container matColumnDef="fundType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let row">{{ row.fundType.replace('_', ' ') }}</td>
          </ng-container>
          <ng-container matColumnDef="openingBalance">
            <th mat-header-cell *matHeaderCellDef>Opening</th>
            <td mat-cell *matCellDef="let row">{{ row.openingBalance | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="totalIncome">
            <th mat-header-cell *matHeaderCellDef>Income</th>
            <td mat-cell *matCellDef="let row" class="positive">{{ row.totalIncome | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="totalExpense">
            <th mat-header-cell *matHeaderCellDef>Expense</th>
            <td mat-cell *matCellDef="let row" class="negative">{{ row.totalExpense | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="balance">
            <th mat-header-cell *matHeaderCellDef>Balance</th>
            <td mat-cell *matCellDef="let row" class="balance">{{ row.balance | number: '1.2-2' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (!rows().length) {
          <p class="no-data">No active funds yet.</p>
        }
      }
    </div>
  `,
  styles: [`
    .loading { display: flex; justify-content: center; padding: 24px; }
    .report-table { width: 100%; }
    .no-data { color: rgba(0,0,0,.54); margin: 8px 0; }
    .positive { color: #2e7d32; }
    .negative { color: #c62828; }
    .balance { font-weight: 600; }
  `],
})
export class FundsOverviewComponent implements OnInit {
  private reportService = inject(FinanceReportService);
  private notifications = inject(NotificationService);

  readonly columns = ['fundName', 'fundType', 'openingBalance', 'totalIncome', 'totalExpense', 'balance'];

  loading = signal(false);
  rows = signal<FundOverviewRow[]>([]);

  ngOnInit(): void {
    this.loading.set(true);
    this.reportService.fundsOverview().subscribe({
      next: rows => { this.rows.set(rows); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
