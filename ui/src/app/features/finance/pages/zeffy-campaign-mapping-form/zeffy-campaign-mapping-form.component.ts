import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ZeffyCampaignMappingRequest } from '../../../../core/models/api.model';
import { Account, Fund, ZeffyCampaign } from '../../../../core/models/domain.model';
import { NotificationService } from '../../../../core/services/notification.service';
import { AccountService } from '../../services/account.service';
import { FundService } from '../../services/fund.service';
import { ZeffyIntegrationService } from '../../services/zeffy-integration.service';

interface ZeffyCampaignMappingDialogData {
  campaign: ZeffyCampaign;
}

@Component({
  selector: 'app-zeffy-campaign-mapping-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>Review Zeffy Campaign</h2>
    <mat-dialog-content>
      <div class="campaign-summary">
        <strong>{{ campaign.title }}</strong>
        <span>{{ campaign.campaignType }}{{ campaign.category ? ' / ' + campaign.category : '' }}</span>
        <span>Zeffy ID: {{ campaign.zeffyCampaignId }}</span>
      </div>

      @if (!campaign.mappingConfirmed) {
        <p class="suggestion">
          Suggested policy: <strong>{{ campaign.suggestedAction }}</strong> with membership credit
          <strong>{{ campaign.suggestedMembershipCredit ? 'enabled' : 'disabled' }}</strong>.
          This suggestion has no effect until you save it.
        </p>
      }

      <form [formGroup]="form" class="mapping-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Processing action</mat-label>
          <mat-select formControlName="action">
            <mat-option value="APPLY">Apply payments to local records</mat-option>
            <mat-option value="IGNORE">Ignore payments from this campaign</mat-option>
          </mat-select>
        </mat-form-field>

        @if (form.controls.action.value === 'APPLY') {
          <div class="form-row">
            <mat-form-field appearance="outline" class="flex-1">
              <mat-label>Fund</mat-label>
              <mat-select formControlName="fundId">
                @for (fund of funds(); track fund.id) {
                  <mat-option [value]="fund.id">{{ fund.fundCode }} - {{ fund.fundName }}</mat-option>
                }
              </mat-select>
              @if (form.controls.fundId.hasError('required')) {
                <mat-error>Select a fund.</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="flex-1">
              <mat-label>Income account</mat-label>
              <mat-select formControlName="categoryAccountId">
                @for (account of revenueAccounts(); track account.id) {
                  <mat-option [value]="account.id">
                    {{ account.accountNumber }} - {{ account.accountName }}
                  </mat-option>
                }
              </mat-select>
              @if (form.controls.categoryAccountId.hasError('required')) {
                <mat-error>Select an income account.</mat-error>
              }
            </mat-form-field>
          </div>

          <mat-checkbox formControlName="grantsMembershipCredit">
            Payments grant membership credit
          </mat-checkbox>
        }

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Policy note (optional)</mat-label>
          <textarea matInput formControlName="note" rows="3" maxlength="500"></textarea>
          <mat-hint align="end">{{ form.controls.note.value.length }}/500</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          Confirm Policy
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .campaign-summary { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; }
    .campaign-summary span { color: rgba(0,0,0,.62); font-size: .9rem; }
    .suggestion { padding: 10px 12px; background: rgba(63,81,181,.08); border-radius: 4px; }
    .mapping-form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; min-width: 520px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    @media (max-width: 650px) {
      .mapping-form { min-width: 0; }
      .form-row { flex-direction: column; gap: 0; }
    }
  `],
})
export class ZeffyCampaignMappingFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ZeffyIntegrationService);
  private readonly fundService = inject(FundService);
  private readonly accountService = inject(AccountService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<ZeffyCampaignMappingFormComponent>);
  private readonly data = inject<ZeffyCampaignMappingDialogData>(MAT_DIALOG_DATA);

  readonly campaign = this.data.campaign;
  readonly saving = signal(false);
  readonly funds = signal<Fund[]>([]);
  readonly revenueAccounts = signal<Account[]>([]);

  readonly form = this.fb.nonNullable.group({
    action: [this.campaign.mappingConfirmed
      ? this.campaign.processingAction ?? 'APPLY'
      : this.campaign.suggestedAction, Validators.required],
    fundId: this.fb.control<string | null>(this.campaign.fund?.id ?? null),
    categoryAccountId: this.fb.control<string | null>(this.campaign.categoryAccount?.id ?? null),
    grantsMembershipCredit: [this.campaign.mappingConfirmed
      ? this.campaign.grantsMembershipCredit
      : this.campaign.suggestedMembershipCredit],
    note: [this.campaign.mappingNote ?? '', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.fundService.getPage({ page: 0, size: 500 }).subscribe(page => this.funds.set(page.content));
    this.accountService.getPage({ page: 0, size: 500 }).subscribe(page =>
      this.revenueAccounts.set(page.content.filter(a => a.accountType === 'revenue')));
  }

  save(): void {
    const apply = this.form.controls.action.value === 'APPLY';
    this.form.controls.fundId.setValidators(apply ? Validators.required : null);
    this.form.controls.categoryAccountId.setValidators(apply ? Validators.required : null);
    this.form.controls.fundId.updateValueAndValidity();
    this.form.controls.categoryAccountId.updateValueAndValidity();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const value = this.form.getRawValue();
    const request: ZeffyCampaignMappingRequest = {
      action: value.action as ZeffyCampaignMappingRequest['action'],
      fundId: apply ? value.fundId ?? undefined : undefined,
      categoryAccountId: apply ? value.categoryAccountId ?? undefined : undefined,
      grantsMembershipCredit: apply && value.grantsMembershipCredit,
      note: value.note.trim() || undefined,
    };
    this.service.updateCampaignMapping(this.campaign.zeffyCampaignId, request).subscribe({
      next: () => {
        this.notifications.success('Zeffy campaign policy confirmed.');
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
