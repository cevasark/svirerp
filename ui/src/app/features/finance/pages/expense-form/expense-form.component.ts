import { Component, OnInit, inject, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FinanceTransactionService } from '../../services/finance-transaction.service';
import { AccountService } from '../../services/account.service';
import { FundService } from '../../services/fund.service';
import { VendorService } from '../../services/vendor.service';
import { VendorFormComponent } from '../vendor-form/vendor-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { Account, Fund, RecordExpenseRequest, Vendor } from '../../../../core/models/domain.model';

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

interface ExpenseDialogData {
}

@Component({
  selector: 'app-expense-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Record Expense</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="expense-form">

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Date</mat-label>
            <input matInput type="date" formControlName="entryDate" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Amount</mat-label>
            <input matInput type="number" step="0.01" formControlName="amount" />
            @if (form.controls.amount.invalid && form.controls.amount.touched) {
              <mat-error>Amount must be greater than 0</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <input matInput formControlName="description" placeholder="e.g. Candles for Sunday service" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryAccountId">
            @for (a of expenseAccounts(); track a.id) {
              <mat-option [value]="a.id">{{ a.accountName }}</mat-option>
            }
          </mat-select>
          @if (form.controls.categoryAccountId.invalid && form.controls.categoryAccountId.touched) {
            <mat-error>Category is required</mat-error>
          }
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Paid From</mat-label>
            <mat-select formControlName="paymentAccountId">
              @for (a of assetAccounts(); track a.id) {
                <mat-option [value]="a.id">{{ a.accountName }}</mat-option>
              }
            </mat-select>
            @if (form.controls.paymentAccountId.invalid && form.controls.paymentAccountId.touched) {
              <mat-error>Required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Payment Method</mat-label>
            <mat-select formControlName="paymentMethod">
              <mat-option value="check">Check</mat-option>
              <mat-option value="cash">Cash</mat-option>
              <mat-option value="bank_transfer">Bank Transfer</mat-option>
              <mat-option value="card">Card</mat-option>
              <mat-option value="zeffy">Zeffy</mat-option>
              <mat-option value="other">Other</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        @if (form.controls.paymentMethod.value === 'check') {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Check Number</mat-label>
            <input matInput formControlName="checkNumber" />
          </mat-form-field>
        }

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Project / Fund (optional)</mat-label>
          <mat-select formControlName="fundId">
            <mat-option [value]="null">— none —</mat-option>
            @for (f of funds(); track f.id) {
              <mat-option [value]="f.id">{{ f.fundName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="vendor-row">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Vendor / Payee (optional)</mat-label>
            <mat-select formControlName="vendorId">
              <mat-option [value]="null">— none —</mat-option>
              @for (v of vendors(); track v.id) {
                <mat-option [value]="v.id">{{ v.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <button mat-icon-button type="button" matTooltip="Vendor not in the list? Add a new one"
                  (click)="openNewVendorDialog()">
            <mat-icon>add_business</mat-icon>
          </button>
        </div>

      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          Record Expense
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .expense-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 460px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .vendor-row { display: flex; align-items: flex-start; gap: 4px; }
    .vendor-row .full-width { flex: 1; }
  `],
})
export class ExpenseFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private transactionService = inject(FinanceTransactionService);
  private accountService = inject(AccountService);
  private fundService = inject(FundService);
  private vendorService = inject(VendorService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<ExpenseFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ExpenseDialogData>(MAT_DIALOG_DATA);

  saving = signal(false);

  accounts = signal<Account[]>([]);
  expenseAccounts = computed(() => this.accounts().filter(a => a.accountType === 'expense' && a.isActive));
  assetAccounts = computed(() => this.accounts().filter(a => a.accountType === 'asset' && a.isActive));
  funds = signal<Fund[]>([]);
  vendors = signal<Vendor[]>([]);

  form = this.fb.nonNullable.group({
    entryDate: [toIsoDate(new Date()), Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    description: [''],
    categoryAccountId: ['', Validators.required],
    paymentAccountId: ['', Validators.required],
    paymentMethod: ['check', Validators.required],
    checkNumber: [''],
    fundId: this.fb.control<string | null>(null),
    vendorId: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    this.accountService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.accounts.set(page.content);
    });
    this.fundService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.funds.set(page.content);
    });
    this.vendorService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.vendors.set(page.content);
    });
  }

  openNewVendorDialog(): void {
    this.dialog
      .open(VendorFormComponent, { width: '560px', data: { vendor: null } })
      .afterClosed()
      .subscribe(saved => {
        if (saved) {
          this.vendorService.getPage({ page: 0, size: 100 }).subscribe(page => {
            this.vendors.set(page.content);
          });
        }
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request: RecordExpenseRequest = {
      entryDate: value.entryDate,
      amount: value.amount,
      description: value.description || undefined,
      categoryAccountId: value.categoryAccountId,
      paymentAccountId: value.paymentAccountId,
      fundId: value.fundId ?? undefined,
      vendorId: value.vendorId ?? undefined,
      paymentMethod: value.paymentMethod as RecordExpenseRequest['paymentMethod'],
      checkNumber: value.paymentMethod === 'check' ? (value.checkNumber || undefined) : undefined,
    };

    this.transactionService.recordExpense(request).subscribe({
      next: () => {
        this.notifications.success('Expense recorded.');
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
