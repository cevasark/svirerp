import {
  Component, inject, OnInit,
  signal, ChangeDetectionStrategy,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { MembershipTypeService } from '../../services/membership-type.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { MembershipType } from '../../../../core/models/domain.model';

interface MembershipTypeDialogData {
  type: MembershipType | null;
}

@Component({
  selector: 'app-membership-type-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Membership Type</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="membership-type-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          @if (form.controls.name.invalid && form.controls.name.touched) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Annual Fee</mat-label>
            <input matInput type="number" step="0.01" min="0" formControlName="annualFee" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Duration (months)</mat-label>
            <input matInput type="number" min="1" formControlName="durationMonths" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Max Members</mat-label>
            <input matInput type="number" min="0" formControlName="maxMembers" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Benefits</mat-label>
          <textarea matInput formControlName="benefits" rows="2"></textarea>
        </mat-form-field>

        <mat-checkbox formControlName="isActive">Active</mat-checkbox>
        <mat-checkbox formControlName="canVote">Can Vote</mat-checkbox>

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
    .membership-type-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
  `],
})
export class MembershipTypeFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private typeService = inject(MembershipTypeService);
  private dialogRef = inject(MatDialogRef<MembershipTypeFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<MembershipTypeDialogData>(MAT_DIALOG_DATA);

  private type = this.data.type;
  isEdit = !!this.type;
  saving = signal(false);

  form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    description: [''],
    annualFee: [0, [Validators.required, Validators.min(0)]],
    durationMonths: [12, [Validators.required, Validators.min(1)]],
    maxMembers: [null as number | null],
    benefits: [''],
    isActive: [true],
    canVote: [false],
  });

  ngOnInit(): void {
    if (this.type) {
      this.form.patchValue({
        name: this.type.name,
        description: this.type.description,
        annualFee: this.type.annualFee,
        durationMonths: this.type.durationMonths,
        maxMembers: this.type.maxMembers ?? null,
        benefits: this.type.benefits,
        isActive: this.type.isActive,
        canVote: this.type.canVote,
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
    // The backend only reads `.getId()` off the nested `org` reference, so a
    const payload = value as Partial<MembershipType>;
    const op = this.isEdit
      ? this.typeService.update(this.type!.id, payload)
      : this.typeService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Membership type ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
