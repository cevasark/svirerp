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
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { Account, Fund, Person, RecordIncomeRequest } from '../../../../core/models/domain.model';

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

interface IncomeFormPrefill {
  serviceRequestId?: string;
  amount?: number;
  description?: string;
  fundId?: string;
}

interface IncomeDialogData {
  prefill?: IncomeFormPrefill;
}

@Component({
  selector: 'app-income-form',
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
    <h2 mat-dialog-title>Record Income</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="income-form">

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
          <input matInput formControlName="description" placeholder="e.g. Zeffy donation — Ivanov family" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Category</mat-label>
          <mat-select formControlName="categoryAccountId">
            @for (a of revenueAccounts(); track a.id) {
              <mat-option [value]="a.id">{{ a.accountName }}</mat-option>
            }
          </mat-select>
          @if (form.controls.categoryAccountId.invalid && form.controls.categoryAccountId.touched) {
            <mat-error>Category is required</mat-error>
          }
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Deposited To</mat-label>
            <mat-select formControlName="depositAccountId">
              @for (a of assetAccounts(); track a.id) {
                <mat-option [value]="a.id">{{ a.accountName }}</mat-option>
              }
            </mat-select>
            @if (form.controls.depositAccountId.invalid && form.controls.depositAccountId.touched) {
              <mat-error>Required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Payment Method</mat-label>
            <mat-select formControlName="paymentMethod">
              <mat-option value="zeffy">Zeffy</mat-option>
              <mat-option value="facebook">Facebook</mat-option>
              <mat-option value="cash">Cash</mat-option>
              <mat-option value="zelle">Zelle</mat-option>
              <mat-option value="check">Check</mat-option>
              <mat-option value="bank_transfer">Bank Transfer</mat-option>
              <mat-option value="card">Card</mat-option>
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

        <div class="person-row">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Donor / Payer (optional)</mat-label>
            <mat-select formControlName="payerId">
              <mat-option [value]="null">— none —</mat-option>
              @for (person of persons(); track person.id) {
                <mat-option [value]="person.id">{{ person.firstName }} {{ person.lastName }} ({{ person.email }})</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <button mat-icon-button type="button" matTooltip="Payer not in the list? Add a new one"
                  (click)="openNewPersonDialog()">
            <mat-icon>person_add</mat-icon>
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
          Record Income
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .income-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 460px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
  `],
})
export class IncomeFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private transactionService = inject(FinanceTransactionService);
  private accountService = inject(AccountService);
  private fundService = inject(FundService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<IncomeFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<IncomeDialogData>(MAT_DIALOG_DATA);

  private serviceRequestId = this.data.prefill?.serviceRequestId ?? null;
  saving = signal(false);

  accounts = signal<Account[]>([]);
  revenueAccounts = computed(() => this.accounts().filter(a => a.accountType === 'revenue' && a.isActive));
  assetAccounts = computed(() => this.accounts().filter(a => a.accountType === 'asset' && a.isActive));
  funds = signal<Fund[]>([]);
  persons = signal<Person[]>([]);

  form = this.fb.nonNullable.group({
    entryDate: [toIsoDate(new Date()), Validators.required],
    amount: [this.data.prefill?.amount ?? 0, [Validators.required, Validators.min(0.01)]],
    description: [this.data.prefill?.description ?? ''],
    categoryAccountId: ['', Validators.required],
    depositAccountId: ['', Validators.required],
    paymentMethod: ['zeffy', Validators.required],
    checkNumber: [''],
    fundId: this.fb.control<string | null>(this.data.prefill?.fundId ?? null),
    payerId: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    this.accountService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.accounts.set(page.content);
    });
    this.fundService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.funds.set(page.content);
    });
    // No search/autocomplete endpoint exists on the backend yet; a plain
    // dropdown over a reasonably-sized page is fine at this org's scale.
    this.personService.getPage({ page: 0, size: 200 }).subscribe(page => {
      this.persons.set(page.content);
    });
  }

  openNewPersonDialog(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((person?: Person) => {
        if (person) {
          this.persons.set([person, ...this.persons()]);
          this.form.patchValue({ payerId: person.id });
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
    const request: RecordIncomeRequest = {
      entryDate: value.entryDate,
      amount: value.amount,
      description: value.description || undefined,
      categoryAccountId: value.categoryAccountId,
      depositAccountId: value.depositAccountId,
      fundId: value.fundId ?? undefined,
      payerId: value.payerId ?? undefined,
      serviceRequestId: this.serviceRequestId ?? undefined,
      paymentMethod: value.paymentMethod as RecordIncomeRequest['paymentMethod'],
      checkNumber: value.paymentMethod === 'check' ? (value.checkNumber || undefined) : undefined,
    };

    this.transactionService.recordIncome(request).subscribe({
      next: () => {
        this.notifications.success('Income recorded.');
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
