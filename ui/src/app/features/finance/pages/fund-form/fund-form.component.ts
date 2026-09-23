import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { FundService } from '../../services/fund.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Fund } from '../../../../core/models/domain.model';

interface FundDialogData {
  fund: Fund | null;
}

@Component({
  selector: 'app-fund-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Project / Fund</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="fund-form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-2">
            <mat-label>Name</mat-label>
            <input matInput formControlName="fundName" placeholder="e.g. Roof Repair 2026" />
            @if (form.controls.fundName.invalid && form.controls.fundName.touched) {
              <mat-error>Name is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Code</mat-label>
            <input matInput formControlName="fundCode" placeholder="e.g. ROOF26" />
            @if (form.controls.fundCode.invalid && form.controls.fundCode.touched) {
              <mat-error>Code is required</mat-error>
            }
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Fund Type</mat-label>
          <mat-select formControlName="fundType">
            <mat-option value="unrestricted">Unrestricted</mat-option>
            <mat-option value="temporarily_restricted">Temporarily Restricted</mat-option>
            <mat-option value="permanently_restricted">Permanently Restricted</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Opening Balance</mat-label>
          <input matInput type="number" step="0.01" formControlName="openingBalance" />
        </mat-form-field>

        <mat-checkbox formControlName="isRestricted">Restricted for a specific purpose</mat-checkbox>

        @if (form.controls.isRestricted.value) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Restriction Purpose</mat-label>
            <textarea matInput formControlName="restrictionPurpose" rows="2"></textarea>
          </mat-form-field>
        }

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
    .fund-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 420px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .flex-2 { flex: 2; }
  `],
})
export class FundFormComponent {
  private fb = inject(FormBuilder);
  private fundService = inject(FundService);
  private dialogRef = inject(MatDialogRef<FundFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<FundDialogData>(MAT_DIALOG_DATA);

  private fund = this.data.fund;
  isEdit = !!this.fund;
  saving = signal(false);

  form = this.fb.nonNullable.group({
    fundName: ['', Validators.required],
    fundCode: ['', Validators.required],
    fundType: ['unrestricted', Validators.required],
    description: [''],
    openingBalance: [0],
    isRestricted: [false],
    restrictionPurpose: [''],
    isActive: [true],
  });

  constructor() {
    if (this.fund) {
      this.form.patchValue({
        fundName: this.fund.fundName,
        fundCode: this.fund.fundCode,
        fundType: this.fund.fundType,
        description: this.fund.description,
        openingBalance: this.fund.openingBalance,
        isRestricted: this.fund.isRestricted,
        restrictionPurpose: this.fund.restrictionPurpose,
        isActive: this.fund.isActive,
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
      fundName: value.fundName,
      fundCode: value.fundCode,
      fundType: value.fundType,
      description: value.description || null,
      openingBalance: value.openingBalance,
      isRestricted: value.isRestricted,
      restrictionPurpose: value.isRestricted ? value.restrictionPurpose || null : null,
      isActive: value.isActive,
    } as unknown as Partial<Fund>;

    const op = this.isEdit
      ? this.fundService.update(this.fund!.id, payload)
      : this.fundService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Fund ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
