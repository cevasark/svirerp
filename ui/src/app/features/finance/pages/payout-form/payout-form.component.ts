import { Component, OnInit, inject, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FinanceTransactionService } from '../../services/finance-transaction.service';
import { AccountService } from '../../services/account.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Account, RecordTransferRequest } from '../../../../core/models/domain.model';

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

interface PayoutDialogData {
}

/**
 * "Record Platform Payout" — the other half of the clearing-account pattern used by the Zeffy
 * import and Stripe webhook (see FinanceService#recordTransfer). Zeffy/Stripe/Facebook hold
 * donations and periodically pay out a net lump sum to the real bank account; this form records
 * that payout as a transfer out of the platform's "Undeposited Funds" account into Checking, once
 * the treasurer sees it land on the actual bank statement.
 */
@Component({
  selector: 'app-payout-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Record Platform Payout</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="payout-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Platform</mat-label>
          <mat-select formControlName="fromAccountId">
            @for (a of clearingAccounts(); track a.id) {
              <mat-option [value]="a.id">{{ a.accountName }}</mat-option>
            }
          </mat-select>
          @if (form.controls.fromAccountId.invalid && form.controls.fromAccountId.touched) {
            <mat-error>Required</mat-error>
          }
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Payout Date</mat-label>
            <input matInput type="date" formControlName="entryDate" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Net Amount</mat-label>
            <input matInput type="number" step="0.01" formControlName="amount" />
            @if (form.controls.amount.invalid && form.controls.amount.touched) {
              <mat-error>Amount must be greater than 0</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <input matInput formControlName="description" placeholder="e.g. Zeffy payout — Aug 2026" />
        </mat-form-field>

      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          Record Payout
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .payout-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 420px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
  `],
})
export class PayoutFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private transactionService = inject(FinanceTransactionService);
  private accountService = inject(AccountService);
  private dialogRef = inject(MatDialogRef<PayoutFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<PayoutDialogData>(MAT_DIALOG_DATA);

  saving = signal(false);

  accounts = signal<Account[]>([]);
  // Undeposited Funds clearing accounts (1020-1029) — see FinanceService#DEFAULT_ACCOUNTS.
  clearingAccounts = computed(() =>
    this.accounts().filter(a => a.accountType === 'asset' && a.isActive && a.accountNumber.startsWith('102')));
  checkingAccountId = computed(() =>
    this.accounts().find(a => a.accountNumber === '1010')?.id ?? '');

  form = this.fb.nonNullable.group({
    entryDate: [toIsoDate(new Date()), Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    description: [''],
    fromAccountId: ['', Validators.required],
  });

  ngOnInit(): void {
    this.accountService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.accounts.set(page.content);
    });
  }

  save(): void {
    if (this.form.invalid || !this.checkingAccountId()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request: RecordTransferRequest = {
      entryDate: value.entryDate,
      amount: value.amount,
      description: value.description || undefined,
      fromAccountId: value.fromAccountId,
      toAccountId: this.checkingAccountId(),
    };

    this.transactionService.recordTransfer(request).subscribe({
      next: () => {
        this.notifications.success('Payout recorded.');
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
