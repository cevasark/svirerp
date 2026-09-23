import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { StripeIntegrationService } from '../../services/stripe-integration.service';
import { FundService } from '../../services/fund.service';
import { AccountService } from '../../services/account.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { StripeProductMapping, Fund, Account } from '../../../../core/models/domain.model';
import { StripePriceInfo, StripeProductMappingRequest } from '../../../../core/models/api.model';

interface StripeProductMappingDialogData {
  mapping: StripeProductMapping | null;
}

const PURPOSE_LABELS: Record<string, string> = {
  membership_dues: 'Membership Dues',
  service_request: 'Church Service (wedding, baptism, etc.)',
  event_ticket: 'Event Ticket (food & drinks)',
  general_income: 'General Income (candles, room rental, other)',
};

/** Routes a Stripe Price — created in the Stripe Dashboard, sold via WordPress or a mobile
 *  card-reader app — to what a completed payment against it means in svirerp. */
@Component({
  selector: 'app-stripe-product-mapping-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Stripe Product Mapping</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="mapping-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Stripe Price</mat-label>
          <mat-select formControlName="stripePriceId">
            @if (isEdit) {
              <mat-option [value]="form.value.stripePriceId">{{ existingPriceLabel() }}</mat-option>
            }
            @for (price of selectablePrices(); track price.priceId) {
              <mat-option [value]="price.priceId">
                {{ price.displayName }} ({{ formatAmount(price) }}) — {{ price.priceId }}
              </mat-option>
            }
          </mat-select>
          @if (!loadingPrices() && selectablePrices().length === 0 && !isEdit) {
            <mat-hint>
              No prices found — configure the Stripe secret key under Settings &gt; Stripe first.
            </mat-hint>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Display Name</mat-label>
          <input matInput formControlName="displayName" placeholder="e.g. Membership — Benefactor" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Purpose</mat-label>
          <mat-select formControlName="purpose">
            @for (key of purposeKeys; track key) {
              <mat-option [value]="key">{{ purposeLabels[key] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (form.value.purpose === 'service_request') {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Service Type</mat-label>
            <mat-select formControlName="serviceType">
              <mat-option value="wedding">Wedding</mat-option>
              <mat-option value="baptism">Baptism</mat-option>
              <mat-option value="funeral">Funeral</mat-option>
              <mat-option value="memorial">Memorial</mat-option>
              <mat-option value="blessing">Blessing</mat-option>
              <mat-option value="other">Other</mat-option>
            </mat-select>
          </mat-form-field>
        }

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Fund (optional)</mat-label>
            <mat-select formControlName="fundId">
              <mat-option [value]="null">— none —</mat-option>
              @for (fund of funds(); track fund.id) {
                <mat-option [value]="fund.id">{{ fund.fundName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Income Account (optional)</mat-label>
            <mat-select formControlName="categoryAccountId">
              <mat-option [value]="null">— use default —</mat-option>
              @for (account of revenueAccounts(); track account.id) {
                <mat-option [value]="account.id">{{ account.accountNumber }} — {{ account.accountName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>
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
    .mapping-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 480px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
  `],
})
export class StripeProductMappingFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private stripeIntegrationService = inject(StripeIntegrationService);
  private fundService = inject(FundService);
  private accountService = inject(AccountService);
  private dialogRef = inject(MatDialogRef<StripeProductMappingFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<StripeProductMappingDialogData>(MAT_DIALOG_DATA);

  private mapping = this.data.mapping;
  isEdit = !!this.mapping;
  saving = signal(false);
  loadingPrices = signal(false);

  purposeLabels = PURPOSE_LABELS;
  purposeKeys = Object.keys(PURPOSE_LABELS);

  funds = signal<Fund[]>([]);
  revenueAccounts = signal<Account[]>([]);
  selectablePrices = signal<StripePriceInfo[]>([]);

  form = this.fb.nonNullable.group({
    stripePriceId: ['', Validators.required],
    displayName: [''],
    purpose: ['membership_dues', Validators.required],
    serviceType: ['other'],
    fundId: this.fb.control<string | null>(null),
    categoryAccountId: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    this.fundService.getPage({ page: 0, size: 200 }).subscribe(page => this.funds.set(page.content));
    this.accountService.getPage({ page: 0, size: 200 }).subscribe(page =>
      this.revenueAccounts.set(page.content.filter(a => a.accountType === 'revenue')));

    this.loadingPrices.set(true);
    this.stripeIntegrationService.getStripePrices().pipe(
      catchError(() => of([] as StripePriceInfo[])),
    ).subscribe(prices => {
      this.loadingPrices.set(false);
      this.selectablePrices.set(this.isEdit ? prices.filter(p => !p.alreadyMapped) : prices);
    });

    if (this.mapping) {
      this.form.patchValue({
        stripePriceId: this.mapping.stripePriceId,
        displayName: this.mapping.displayName,
        purpose: this.mapping.purpose,
        serviceType: this.mapping.serviceType ?? 'other',
        fundId: this.mapping.fund?.id ?? null,
        categoryAccountId: this.mapping.categoryAccount?.id ?? null,
      });
    }
  }

  existingPriceLabel(): string {
    return this.mapping?.displayName
      ? `${this.mapping.displayName} — ${this.mapping.stripePriceId}`
      : this.mapping?.stripePriceId ?? '';
  }

  formatAmount(price: StripePriceInfo): string {
    if (price.unitAmount == null) return '—';
    return `${(price.unitAmount / 100).toFixed(2)} ${(price.currency ?? '').toUpperCase()}`;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const payload: StripeProductMappingRequest = {
      stripePriceId: value.stripePriceId,
      displayName: value.displayName || undefined,
      purpose: value.purpose as StripeProductMappingRequest['purpose'],
      serviceType: value.purpose === 'service_request' ? value.serviceType : undefined,
      fundId: value.fundId || undefined,
      categoryAccountId: value.categoryAccountId || undefined,
    };

    const op = this.isEdit
      ? this.stripeIntegrationService.updateMapping(this.mapping!.id, payload)
      : this.stripeIntegrationService.createMapping(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Mapping ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
