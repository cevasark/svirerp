import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CurrencyPipe } from '@angular/common';

import { RoomRentalService } from '../../../services/room-rental.service';
import { NotificationService } from '../../../../../core/services/notification.service';
import { RoomRental, RentalPaymentMethod } from '../../../../../core/models/domain.model';

export interface RentalPaymentDialogData {
  rental: RoomRental;
  paymentType: 'deposit' | 'final';
}

const PAYMENT_METHODS: { value: RentalPaymentMethod; label: string }[] = [
  { value: 'cash', label: 'Cash' },
  { value: 'check', label: 'Check' },
  { value: 'zelle', label: 'Zelle' },
  { value: 'card', label: 'Card' },
  { value: 'other', label: 'Other' },
];

function toIsoDate(d: Date = new Date()): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-rental-payment-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      {{ data.paymentType === 'deposit' ? 'Record Deposit Payment' : 'Record Final Balance Payment' }}
    </h2>

    <mat-dialog-content>
      <div class="rental-summary">
        <div class="summary-line">
          <span class="label">Event:</span>
          <strong>{{ data.rental.eventTitle }}</strong>
        </div>
        <div class="summary-line">
          <span class="label">Renter:</span>
          <span>{{ data.rental.renterPerson.firstName }} {{ data.rental.renterPerson.lastName }}</span>
        </div>
        <div class="summary-grid">
          <div class="stat-box">
            <span class="stat-label">Total Fee</span>
            <span class="stat-val">{{ data.rental.totalFee | currency }}</span>
          </div>
          <div class="stat-box">
            <span class="stat-label">Deposit</span>
            <span class="stat-val">{{ data.rental.depositAmount | currency }}</span>
          </div>
          <div class="stat-box highlight">
            <span class="stat-label">{{ data.paymentType === 'deposit' ? 'Deposit Expected' : 'Balance Remaining' }}</span>
            <span class="stat-val">{{ expectedAmount() | currency }}</span>
          </div>
        </div>
      </div>

      <form [formGroup]="form" class="payment-form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Payment Amount</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="amount" />
            <span matTextPrefix>$&nbsp;</span>
            @if (form.controls.amount.invalid && form.controls.amount.touched) {
              <mat-error>Valid positive amount is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Payment Date</mat-label>
            <input matInput type="date" formControlName="paymentDate" />
            @if (form.controls.paymentDate.invalid && form.controls.paymentDate.touched) {
              <mat-error>Payment date is required</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Payment Method</mat-label>
          <mat-select formControlName="paymentMethod">
            @for (m of paymentMethods; track m.value) {
              <mat-option [value]="m.value">{{ m.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Payment Notes (optional)</mat-label>
          <textarea matInput formControlName="notes" rows="2" placeholder="e.g., Cash handed to trustee, Zelle confirmation"></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="18" mode="indeterminate" />
        } @else {
          Save Payment & Advance Stage
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .rental-summary {
      background: #f8fafc;
      border: 1px solid rgba(0,0,0,.08);
      border-radius: 8px;
      padding: 12px 14px;
      margin-bottom: 16px;
    }
    .summary-line {
      display: flex;
      gap: 8px;
      font-size: .95rem;
      margin-bottom: 4px;
    }
    .summary-line .label {
      color: rgba(0,0,0,.6);
      width: 60px;
    }
    .summary-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 8px;
      margin-top: 10px;
      padding-top: 8px;
      border-top: 1px dashed rgba(0,0,0,.12);
    }
    .stat-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 4px 6px;
      border-radius: 4px;
      background: rgba(0,0,0,.03);
    }
    .stat-box.highlight {
      background: #e0f2fe;
      color: #0369a1;
    }
    .stat-label {
      font-size: .75rem;
      text-transform: uppercase;
      font-weight: 600;
      color: rgba(0,0,0,.55);
    }
    .stat-box.highlight .stat-label {
      color: #0369a1;
    }
    .stat-val {
      font-size: 1rem;
      font-weight: 700;
    }
    .payment-form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding-top: 4px;
    }
    .form-row {
      display: flex;
      gap: 12px;
    }
    .flex-1 { flex: 1; }
    .full-width { width: 100%; }
  `],
})
export class RentalPaymentDialogComponent implements OnInit {
  private fb = inject(FormBuilder);
  private rentalService = inject(RoomRentalService);
  private notifications = inject(NotificationService);
  private dialogRef = inject(MatDialogRef<RentalPaymentDialogComponent>);
  readonly data = inject<RentalPaymentDialogData>(MAT_DIALOG_DATA);

  saving = signal(false);
  readonly paymentMethods = PAYMENT_METHODS;

  form = this.fb.nonNullable.group({
    amount: [0, [Validators.required, Validators.min(0.01)]],
    paymentDate: [toIsoDate(), Validators.required],
    paymentMethod: ['cash' as RentalPaymentMethod, Validators.required],
    notes: [''],
  });

  expectedAmount(): number {
    const r = this.data.rental;
    if (this.data.paymentType === 'deposit') {
      return r.depositAmount > 0 ? r.depositAmount : r.totalFee * 0.5;
    }
    const balance = r.totalFee - (r.depositAmount || 0) - (r.finalAmountPaid || 0);
    return Math.max(0, balance);
  }

  ngOnInit(): void {
    const expected = this.expectedAmount();
    this.form.patchValue({
      amount: expected > 0 ? expected : 0,
      paymentDate: toIsoDate(),
      paymentMethod: (this.data.paymentType === 'deposit'
        ? this.data.rental.depositPaymentMethod
        : this.data.rental.finalPaymentMethod) || 'cash',
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const val = this.form.getRawValue();
    const r = this.data.rental;

    const payload: Partial<RoomRental> = {
      id: r.id,
      roomName: r.roomName,
      eventTitle: r.eventTitle,
      startTime: r.startTime,
      endTime: r.endTime,
      estimatedAttendees: r.estimatedAttendees,
      totalFee: r.totalFee,
      renterPerson: { id: r.renterPerson.id } as any,
      contactTrustee: r.contactTrustee ? ({ id: r.contactTrustee.id } as any) : undefined,
    };

    if (this.data.paymentType === 'deposit') {
      payload.depositAmount = val.amount;
      payload.depositPaidDate = val.paymentDate;
      payload.depositPaymentMethod = val.paymentMethod;
      payload.status = 'deposit_paid';
      payload.finalAmountPaid = r.finalAmountPaid || 0;
      payload.finalPaymentDate = r.finalPaymentDate;
      payload.finalPaymentMethod = r.finalPaymentMethod;
    } else {
      payload.depositAmount = r.depositAmount || 0;
      payload.depositPaidDate = r.depositPaidDate;
      payload.depositPaymentMethod = r.depositPaymentMethod;
      payload.finalAmountPaid = (r.finalAmountPaid || 0) + val.amount;
      payload.finalPaymentDate = val.paymentDate;
      payload.finalPaymentMethod = val.paymentMethod;
      payload.status = 'confirmed';
    }

    if (val.notes.trim()) {
      payload.notes = r.notes ? `${r.notes}\n[${val.paymentDate} Payment]: ${val.notes.trim()}` : `[${val.paymentDate} Payment]: ${val.notes.trim()}`;
    } else {
      payload.notes = r.notes;
    }

    this.rentalService.update(r.id, payload).subscribe({
      next: updated => {
        this.notifications.success(
          this.data.paymentType === 'deposit'
            ? 'Deposit recorded. Spot is now reserved!'
            : 'Final payment recorded. Reservation is fully confirmed!',
        );
        this.dialogRef.close(updated);
      },
      error: () => this.saving.set(false),
    });
  }
}
