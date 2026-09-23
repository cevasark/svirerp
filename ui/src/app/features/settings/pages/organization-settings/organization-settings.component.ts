import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { OrganizationService } from '../../../organizations/services/organization.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

/**
 * This is a single-organization installation, so there is
 * no organization list/picker anywhere else, so this is the one place the
 * org's own details are viewable/editable, embedded directly in Settings
 * rather than behind a dialog, matching the rest of the Settings page's
 * in-place-editing convention.
 */
@Component({
  selector: 'app-organization-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Organization"
        subtitle="Details for the single organization this installation manages" />

      <mat-card>
        <mat-card-content>
          <form [formGroup]="form" class="organization-form">

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Name</mat-label>
              <input matInput formControlName="name" autocomplete="organization" />
              @if (form.controls.name.invalid && form.controls.name.touched) {
                <mat-error>Name is required</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Legal Name</mat-label>
              <input matInput formControlName="legalName" />
            </mat-form-field>

            <div class="form-row">
              <mat-form-field appearance="outline" class="flex-1">
                <mat-label>Tax ID / EIN</mat-label>
                <input matInput formControlName="taxIdEin" />
              </mat-form-field>

              <mat-form-field appearance="outline" class="flex-1">
                <mat-label>Nonprofit Type</mat-label>
                <input matInput formControlName="nonprofitType" placeholder="e.g. 501(c)(3)" />
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="email" />
              @if (form.controls.email.hasError('email')) {
                <mat-error>Enter a valid email address</mat-error>
              }
            </mat-form-field>

            <div class="form-row">
              <mat-form-field appearance="outline" class="flex-1">
                <mat-label>Phone</mat-label>
                <input matInput type="tel" formControlName="phone" autocomplete="tel" />
              </mat-form-field>

              <mat-form-field appearance="outline" class="flex-1">
                <mat-label>Website</mat-label>
                <input matInput formControlName="website" autocomplete="url" />
              </mat-form-field>
            </div>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Address</mat-label>
              <input matInput formControlName="addressLine1" autocomplete="street-address" />
            </mat-form-field>

            <div class="form-row">
              <mat-form-field appearance="outline" class="flex-2">
                <mat-label>City</mat-label>
                <input matInput formControlName="city" autocomplete="address-level2" />
              </mat-form-field>

              <mat-form-field appearance="outline" class="flex-1">
                <mat-label>State</mat-label>
                <input matInput formControlName="state" maxlength="2" autocomplete="address-level1" />
              </mat-form-field>

              <mat-form-field appearance="outline" class="flex-1">
                <mat-label>ZIP</mat-label>
                <input matInput formControlName="zip" autocomplete="postal-code" />
              </mat-form-field>
            </div>

            <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
              @if (saving()) {
                <mat-progress-spinner diameter="20" mode="indeterminate" />
              } @else {
                {{ isEdit() ? 'Save Changes' : 'Create Organization' }}
              }
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .organization-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; max-width: 640px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .flex-2 { flex: 2; }
  `],
})
export class OrganizationSettingsComponent implements OnInit {
  private fb = inject(FormBuilder);
  private orgService = inject(OrganizationService);
  private notifications = inject(NotificationService);

  isEdit = signal(false);
  saving = signal(false);

  form = this.fb.nonNullable.group({
    name:           ['', Validators.required],
    legalName:      [''],
    taxIdEin:       [''],
    nonprofitType:  [''],
    email:          ['', Validators.email],
    phone:          [''],
    website:        [''],
    addressLine1:   [''],
    city:           [''],
    state:          [''],
    zip:            [''],
  });

  ngOnInit(): void {
    this.orgService.get().subscribe({
      next: org => {
        this.isEdit.set(true);
        this.form.patchValue(org);
      },
      error: () => this.isEdit.set(false),
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const wasEdit = this.isEdit();
    const payload = this.form.getRawValue();
    this.orgService.save(payload).subscribe({
      next: org => {
        this.isEdit.set(true);
        this.saving.set(false);
        this.notifications.success(`Organization ${wasEdit ? 'updated' : 'created'}.`);
      },
      error: () => this.saving.set(false),
    });
  }
}
