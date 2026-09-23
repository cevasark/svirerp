import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AccountService } from '../../services/account.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Account } from '../../../../core/models/domain.model';

interface AccountDialogData {
  account: Account | null;
}

/** Asset and expense accounts normally carry a debit balance; liability, equity and revenue carry credit. */
function normalBalanceFor(accountType: string): 'debit' | 'credit' {
  return accountType === 'asset' || accountType === 'expense' ? 'debit' : 'credit';
}

@Component({
  selector: 'app-account-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Category</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="account-form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Account Number</mat-label>
            <input matInput formControlName="accountNumber" placeholder="e.g. 5040" />
            @if (form.controls.accountNumber.invalid && form.controls.accountNumber.touched) {
              <mat-error>Required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-2">
            <mat-label>Name</mat-label>
            <input matInput formControlName="accountName" placeholder="e.g. Candles &amp; Church Supplies" />
            @if (form.controls.accountName.invalid && form.controls.accountName.touched) {
              <mat-error>Required</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Type</mat-label>
          <mat-select formControlName="accountType" [disabled]="isEdit">
            <mat-option value="revenue">Revenue (income)</mat-option>
            <mat-option value="expense">Expense</mat-option>
            <mat-option value="asset">Asset</mat-option>
            <mat-option value="liability">Liability</mat-option>
            <mat-option value="equity">Equity</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <mat-checkbox formControlName="isActive">Active</mat-checkbox>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          {{ isEdit ? 'Save Changes' : 'Create' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .account-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 420px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .flex-2 { flex: 2; }
  `],
})
export class AccountFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private accountService = inject(AccountService);
  private dialogRef = inject(MatDialogRef<AccountFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<AccountDialogData>(MAT_DIALOG_DATA);

  private account = this.data.account;
  isEdit = !!this.account;
  saving = signal(false);

  form = this.fb.nonNullable.group({
    accountNumber: ['', Validators.required],
    accountName: ['', Validators.required],
    accountType: ['expense', Validators.required],
    description: [''],
    isActive: [true],
  });

  ngOnInit(): void {
    if (this.account) {
      this.form.patchValue({
        accountNumber: this.account.accountNumber,
        accountName: this.account.accountName,
        accountType: this.account.accountType,
        description: this.account.description,
        isActive: this.account.isActive,
      });
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const payload = {
      accountNumber: value.accountNumber,
      accountName: value.accountName,
      accountType: value.accountType,
      normalBalance: normalBalanceFor(value.accountType),
      description: value.description || null,
      isActive: value.isActive,
    } as unknown as Partial<Account>;

    const op = this.isEdit
      ? this.accountService.update(this.account!.id, payload)
      : this.accountService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Category ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
