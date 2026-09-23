import {
  Component, inject, OnInit,
  signal, ChangeDetectionStrategy,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { MemberService } from '../../services/member.service';
import { MemberPaymentService } from '../../services/member-payment.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Member, MemberPayment } from '../../../../core/models/domain.model';

interface MemberPaymentDialogData {
  member: Member | null;
  entity: MemberPayment | null;
}

@Component({
  selector: 'app-member-payment-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Record' }} Contribution</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="payment-form">

        @if (fixedMember) {
          <p class="fixed-member">For {{ fixedMember.person.firstName }} {{ fixedMember.person.lastName }}</p>
        } @else {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Member</mat-label>
            <mat-select formControlName="memberId">
              @for (m of members(); track m.id) {
                <mat-option [value]="m.id">{{ m.person.firstName }} {{ m.person.lastName }} ({{ m.person.email }})</mat-option>
              }
            </mat-select>
            @if (form.controls.memberId.invalid && form.controls.memberId.touched) {
              <mat-error>Member is required</mat-error>
            }
          </mat-form-field>
        }

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Amount</mat-label>
            <input matInput type="number" step="0.01" min="0.01" formControlName="amount" />
            @if (form.controls.amount.invalid && form.controls.amount.touched) {
              <mat-error>Enter an amount greater than 0</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Date</mat-label>
            <input matInput type="date" formControlName="paymentDate" />
            @if (form.controls.paymentDate.invalid && form.controls.paymentDate.touched) {
              <mat-error>Date is required</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Notes</mat-label>
          <textarea matInput formControlName="notes" rows="2"></textarea>
        </mat-form-field>

      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          {{ isEdit ? 'Save Changes' : 'Record' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .payment-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 380px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .fixed-member { margin: 0 0 8px; color: rgba(0,0,0,.6); }
  `],
})
export class MemberPaymentFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private memberService = inject(MemberService);
  private paymentService = inject(MemberPaymentService);
  private dialogRef = inject(MatDialogRef<MemberPaymentFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<MemberPaymentDialogData>(MAT_DIALOG_DATA);

  fixedMember = this.data.member;
  private entity = this.data.entity;
  isEdit = !!this.entity;
  saving = signal(false);

  members = signal<Member[]>([]);

  form = this.fb.nonNullable.group({
    memberId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    paymentDate: ['', Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    if (!this.fixedMember) {
      // No search/autocomplete endpoint exists on the backend yet; a plain
      // dropdown over a reasonably-sized page is fine at this org's scale.
      this.memberService.getPage({ page: 0, size: 500 })
        .subscribe(page => this.members.set(page.content));
    }

    if (this.entity) {
      this.form.patchValue({
        memberId: this.entity.member.id,
        amount: this.entity.amount,
        paymentDate: this.entity.paymentDate,
        notes: this.entity.notes ?? '',
      });
    } else if (this.fixedMember) {
      this.form.controls.memberId.setValue(this.fixedMember.id);
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    // Backend only reads .getId() off the nested member reference on write.
    const payload = {
      member: { id: value.memberId },
      amount: value.amount,
      paymentDate: value.paymentDate,
      notes: value.notes || null,
    } as unknown as Partial<MemberPayment>;

    const op = this.isEdit
      ? this.paymentService.update(this.entity!.id, payload)
      : this.paymentService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Contribution ${this.isEdit ? 'updated' : 'recorded'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
