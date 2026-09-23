import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { VendorService } from '../../services/vendor.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Vendor } from '../../../../core/models/domain.model';

interface VendorDialogData {
  vendor: Vendor | null;
}

@Component({
  selector: 'app-vendor-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Vendor</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="vendor-form">
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-2">
            <mat-label>Name</mat-label>
            <input matInput formControlName="name" placeholder="e.g. City Utilities" />
            @if (form.controls.name.invalid && form.controls.name.touched) {
              <mat-error>Name is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Category</mat-label>
            <input matInput formControlName="category" placeholder="e.g. Utilities" />
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Contact Name</mat-label>
            <input matInput formControlName="contactName" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Phone</mat-label>
            <input matInput formControlName="phone" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Email</mat-label>
          <input matInput formControlName="email" type="email" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Address</mat-label>
          <input matInput formControlName="addressLine1" />
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>City</mat-label>
            <input matInput formControlName="city" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>State</mat-label>
            <input matInput formControlName="state" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Zip</mat-label>
            <input matInput formControlName="zip" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Notes</mat-label>
          <textarea matInput formControlName="notes" rows="2"></textarea>
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
    .vendor-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 460px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .flex-2 { flex: 2; }
  `],
})
export class VendorFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private vendorService = inject(VendorService);
  private dialogRef = inject(MatDialogRef<VendorFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<VendorDialogData>(MAT_DIALOG_DATA);

  private vendor = this.data.vendor;
  isEdit = !!this.vendor;
  saving = signal(false);

  form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    category: [''],
    contactName: [''],
    phone: [''],
    email: [''],
    addressLine1: [''],
    city: [''],
    state: [''],
    zip: [''],
    notes: [''],
    isActive: [true],
  });

  ngOnInit(): void {
    if (this.vendor) {
      this.form.patchValue({
        name: this.vendor.name,
        category: this.vendor.category,
        contactName: this.vendor.contactName,
        phone: this.vendor.phone,
        email: this.vendor.email,
        addressLine1: this.vendor.addressLine1,
        city: this.vendor.city,
        state: this.vendor.state,
        zip: this.vendor.zip,
        notes: this.vendor.notes,
        isActive: this.vendor.isActive,
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
      name: value.name,
      category: value.category || null,
      contactName: value.contactName || null,
      phone: value.phone || null,
      email: value.email || null,
      addressLine1: value.addressLine1 || null,
      city: value.city || null,
      state: value.state || null,
      zip: value.zip || null,
      notes: value.notes || null,
      isActive: value.isActive,
    } as unknown as Partial<Vendor>;

    const op = this.isEdit
      ? this.vendorService.update(this.vendor!.id, payload)
      : this.vendorService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Vendor ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
