import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';

import {
  Page, PageParams, ZeffyIntegrationStatus, ZeffyPaymentSyncItem, ZeffySyncRun,
} from '../../../../core/models/api.model';
import { NotificationService } from '../../../../core/services/notification.service';
import { DataTableComponent, TableColumn } from '../../../../shared/components/data-table/data-table.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ZeffyIntegrationService } from '../../../finance/services/zeffy-integration.service';

@Component({
  selector: 'app-zeffy-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    PageHeaderComponent,
    DataTableComponent,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  template: `
    <div class="page-container">
      <app-page-header title="Zeffy"
        subtitle="Manage the Zeffy API, campaign catalog, and signed webhook receiver" />

      <mat-card class="setting-card">
        <mat-card-content>
          <h3>Connection</h3>
          <div class="status-grid">
            <span>API key</span><strong>{{ status()?.apiKeyConfigured ? 'Configured' : 'Not configured' }}</strong>
            <span>Webhook secret</span><strong>{{ status()?.webhookSecretConfigured ? 'Configured' : 'Not configured' }}</strong>
            <span>Event-processing mode</span><strong>{{ status()?.integrationMode ?? 'DISABLED' }}</strong>
            <span>Last connection test</span><strong>{{ displayDate(lastConnectionTestAt()) }}</strong>
          </div>
          <p class="hint">
            API requests are limited to one request per second. Webhooks are independently
            authenticated with the signing secret below.
          </p>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>API key</mat-label>
            <input matInput type="password" [(ngModel)]="apiKeyDraft"
              [placeholder]="status()?.apiKeyConfigured ? 'Configured - enter a value to replace' : 'Zeffy API key'"
              autocomplete="new-password" />
          </mat-form-field>
          <mat-checkbox [(ngModel)]="validateApiKey" [disabled]="!apiKeyDraft">
            Validate replacement key before saving
          </mat-checkbox>

          <mat-form-field appearance="outline" class="full-width secret-field">
            <mat-label>Webhook signing secret</mat-label>
            <input matInput type="password" [(ngModel)]="webhookSecretDraft"
              [placeholder]="status()?.webhookSecretConfigured ? 'Configured - enter a value to replace' : 'whsec_...'"
              autocomplete="new-password" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Webhook mode</mat-label>
            <mat-select [(ngModel)]="modeDraft">
              <mat-option value="DISABLED">Disabled</mat-option>
              <mat-option value="RECORD_ONLY">Record only</mat-option>
              <mat-option value="LIVE">Live</mat-option>
            </mat-select>
          </mat-form-field>
          <p class="hint">
            Record only verifies and stores events. Live applies completed payments using confirmed
            campaign mappings and can create people, membership contributions, and journal entries.
          </p>

          <div class="button-row">
            <button mat-flat-button color="primary"
              [disabled]="saving() || (!apiKeyDraft && !webhookSecretDraft && modeDraft === status()?.integrationMode)"
              (click)="save()">
              @if (saving()) { <mat-progress-spinner diameter="20" mode="indeterminate" /> }
              @else { Save Configuration }
            </button>
            <button mat-stroked-button [disabled]="testing() || !status()?.apiKeyConfigured"
              (click)="testConnection()">
              @if (testing()) { <mat-progress-spinner diameter="20" mode="indeterminate" /> }
              @else { Test Connection }
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      <mat-card class="setting-card">
        <mat-card-content>
          <h3>Campaign synchronization</h3>
          <div class="status-grid">
            <span>Latest run</span><strong>{{ latestRunLabel() }}</strong>
            <span>Campaigns stored</span><strong>{{ status()?.campaignCount ?? 0 }}</strong>
            <span>Mappings confirmed</span><strong>{{ status()?.confirmedMappingCount ?? 0 }}</strong>
            <span>Latest counts</span><strong>{{ latestCounts() }}</strong>
          </div>
          <p class="hint">
            Synchronization requests 100 campaigns per page and records every attempt below.
            Existing local campaign policies are preserved.
          </p>
          <button mat-flat-button color="primary"
            [disabled]="syncing() || !status()?.apiKeyConfigured" (click)="syncCampaigns()">
            @if (syncing()) { <mat-progress-spinner diameter="20" mode="indeterminate" /> }
            @else { Sync Campaigns }
          </button>
        </mat-card-content>
      </mat-card>

      <mat-card class="setting-card">
        <mat-card-content>
          <h3>Historical payments</h3>
          <p class="hint">
            Preview succeeded Zeffy payments before applying them. Preview updates only integration
            snapshots and creates no people, membership contributions, or journal entries. Dates use
            the church's America/Chicago timezone.
          </p>
          <div class="date-grid">
            <mat-form-field appearance="outline">
              <mat-label>Created from</mat-label>
              <input matInput type="date" [(ngModel)]="paymentCreatedFrom" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Created through (optional)</mat-label>
              <input matInput type="date" [(ngModel)]="paymentCreatedThrough" />
            </mat-form-field>
          </div>
          <div class="status-grid">
            <span>Latest preview</span><strong>{{ paymentRunLabel(status()?.latestPaymentPreview) }}</strong>
            <span>Latest payment run</span><strong>{{ paymentRunLabel(status()?.latestPaymentSync) }}</strong>
            <span>Latest result</span><strong>{{ paymentCounts(status()?.latestPaymentSync) }}</strong>
          </div>
          <div class="button-row">
            <button mat-flat-button color="primary"
              [disabled]="paymentSyncing() || !status()?.apiKeyConfigured || !paymentCreatedFrom"
              (click)="previewPayments()">
              @if (paymentSyncing()) { <mat-progress-spinner diameter="20" mode="indeterminate" /> }
              @else { Preview Payments }
            </button>
            <button mat-stroked-button
              [disabled]="paymentSyncing() || !canApplyLatestPreview()"
              (click)="applyLatestPreview()">
              Apply Latest Preview
            </button>
            <button mat-stroked-button [disabled]="!status()?.latestPaymentSync"
              (click)="viewLatestPaymentResults()">
              View Latest Results
            </button>
          </div>
        </mat-card-content>
      </mat-card>

      @if (paymentResults()) {
        <mat-card class="setting-card">
          <mat-card-content>
            <h3>Historical payment results</h3>
            <p class="hint">Run {{ selectedPaymentRunId() }}</p>
            <app-data-table
              [columns]="paymentResultColumns"
              [data]="paymentResults()"
              [loading]="loadingPaymentResults()"
              [pageParams]="paymentResultPageParams()"
              emptyMessage="This run has no payment results."
              (pageChange)="onPaymentResultPageChange($event)" />
          </mat-card-content>
        </mat-card>
      }

      <mat-card class="setting-card">
        <mat-card-content>
          <h3>Synchronization history</h3>
          <app-data-table
            [columns]="runColumns"
            [data]="runs()"
            [loading]="loadingRuns()"
            [pageParams]="runPageParams()"
            emptyMessage="No Zeffy synchronization runs yet."
            (pageChange)="onRunPageChange($event)" />
        </mat-card-content>
      </mat-card>

      <mat-card class="setting-card">
        <mat-card-content>
          <h3>Webhook endpoint</h3>
          <code>{{ webhookUrl() }}</code>
          <div class="button-row">
            <button mat-stroked-button (click)="copyWebhookUrl()">Copy URL</button>
          </div>
          <p class="hint">
            Configure this HTTPS URL under Zeffy Settings &gt; Integrations, copy the generated
            whsec_ signing secret above, then select Record only.
          </p>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .setting-card { margin-bottom: 16px; }
    h3 { margin: 0 0 16px; }
    .full-width { width: 100%; display: block; }
    .secret-field { margin-top: 16px; }
    .hint { color: rgba(0,0,0,.62); font-size: .9rem; line-height: 1.4; }
    .status-grid { display: grid; grid-template-columns: minmax(150px, 220px) 1fr; gap: 8px 16px; margin-bottom: 16px; }
    .button-row { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 16px; }
    .date-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }
    mat-progress-spinner { display: inline-block; }
    code { overflow-wrap: anywhere; }
  `],
})
export class ZeffySettingsComponent implements OnInit {
  private readonly service = inject(ZeffyIntegrationService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly status = signal<ZeffyIntegrationStatus | null>(null);
  readonly runs = signal<Page<ZeffySyncRun> | null>(null);
  readonly paymentResults = signal<Page<ZeffyPaymentSyncItem> | null>(null);
  readonly selectedPaymentRunId = signal<string | null>(null);
  readonly runPageParams = signal<PageParams>({ page: 0, size: 10, sort: 'startedAt,desc' });
  readonly paymentResultPageParams = signal<PageParams>({ page: 0, size: 20, sort: 'observedAt,desc' });
  readonly lastConnectionTestAt = signal<string | undefined>(undefined);
  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly syncing = signal(false);
  readonly paymentSyncing = signal(false);
  readonly loadingRuns = signal(false);
  readonly loadingPaymentResults = signal(false);

  apiKeyDraft = '';
  webhookSecretDraft = '';
  validateApiKey = true;
  modeDraft: 'DISABLED' | 'RECORD_ONLY' | 'LIVE' = 'DISABLED';
  paymentCreatedFrom = '';
  paymentCreatedThrough = '';

  readonly runColumns: TableColumn[] = [
    { key: 'startedAt', header: 'Started', type: 'date', cell: (r: ZeffySyncRun) => this.displayDate(r.startedAt) },
    { key: 'syncType', header: 'Type', type: 'status' },
    { key: 'executionMode', header: 'Mode', type: 'status', cell: (r: ZeffySyncRun) => r.executionMode ?? '-' },
    { key: 'status', header: 'Status', type: 'status' },
    { key: 'counts', header: 'Result', cell: (r: ZeffySyncRun) => this.runCounts(r) },
    { key: 'initiatedBy', header: 'Initiated By', cell: (r: ZeffySyncRun) => r.initiatedBy ?? 'System' },
    { key: 'errorSummary', header: 'Error', cell: (r: ZeffySyncRun) => r.errorSummary ?? '-' },
  ];

  readonly paymentResultColumns: TableColumn[] = [
    { key: 'zeffyPaymentId', header: 'Zeffy Payment' },
    { key: 'outcome', header: 'Outcome', type: 'status' },
    { key: 'amount', header: 'Amount', cell: (r: ZeffyPaymentSyncItem) => this.paymentAmount(r) },
    { key: 'campaignTitle', header: 'Campaign', cell: (r: ZeffyPaymentSyncItem) => r.campaignTitle ?? '-' },
    { key: 'buyerEmail', header: 'Buyer', cell: (r: ZeffyPaymentSyncItem) => r.buyerEmail ?? '-' },
    { key: 'detail', header: 'Detail', cell: (r: ZeffyPaymentSyncItem) => r.detail ?? '-' },
  ];

  ngOnInit(): void {
    this.loadStatus();
    this.loadRuns();
  }

  save(): void {
    this.saving.set(true);
    this.service.saveConfiguration({
      apiKey: this.apiKeyDraft || undefined,
      webhookSigningSecret: this.webhookSecretDraft || undefined,
      validateApiKey: !!this.apiKeyDraft && this.validateApiKey,
      integrationMode: this.modeDraft,
    }).subscribe({
      next: status => {
        this.status.set(status);
        this.apiKeyDraft = '';
        this.webhookSecretDraft = '';
        this.saving.set(false);
        this.modeDraft = status.integrationMode;
        this.notifications.success('Zeffy configuration saved.');
      },
      error: () => this.saving.set(false),
    });
  }

  testConnection(): void {
    this.testing.set(true);
    this.service.testConnection().subscribe({
      next: result => {
        this.lastConnectionTestAt.set(result.testedAt);
        this.testing.set(false);
        this.notifications.success('Zeffy API connection succeeded.');
      },
      error: () => this.testing.set(false),
    });
  }

  syncCampaigns(): void {
    this.syncing.set(true);
    this.service.syncCampaigns().subscribe({
      next: result => {
        this.syncing.set(false);
        this.notifications.success(`Campaign sync ${result.run.status.toLowerCase()}.`);
        this.loadStatus();
        this.loadRuns();
      },
      error: () => {
        this.syncing.set(false);
        this.loadStatus();
        this.loadRuns();
      },
    });
  }

  previewPayments(): void {
    if (!this.paymentCreatedFrom) return;
    this.paymentSyncing.set(true);
    this.service.syncPayments({
      mode: 'PREVIEW',
      createdFrom: this.paymentCreatedFrom,
      createdThrough: this.paymentCreatedThrough || undefined,
    }).subscribe({
      next: result => this.paymentSyncCompleted(result.run, 'Payment preview'),
      error: () => this.paymentSyncFailed(),
    });
  }

  applyLatestPreview(): void {
    const preview = this.status()?.latestPaymentPreview;
    if (!preview) return;
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Apply Historical Payments',
        message: `Apply eligible payments from the preview started ${this.displayDate(preview.startedAt)}? `
          + 'This can create people, membership contributions, and journal entries.',
        confirmLabel: 'Apply Payments',
      },
    }).afterClosed().subscribe(confirmed => {
      if (confirmed) this.executePaymentApply(preview.id);
    });
  }

  private executePaymentApply(previewRunId: string): void {
    this.paymentSyncing.set(true);
    this.service.syncPayments({ mode: 'APPLY', previewRunId }).subscribe({
      next: result => this.paymentSyncCompleted(result.run, 'Payment apply'),
      error: () => this.paymentSyncFailed(),
    });
  }

  canApplyLatestPreview(): boolean {
    const preview = this.status()?.latestPaymentPreview;
    return !!this.status()?.apiKeyConfigured && !!preview && preview.status === 'COMPLETED';
  }

  viewLatestPaymentResults(): void {
    const run = this.status()?.latestPaymentSync;
    if (!run) return;
    this.selectedPaymentRunId.set(run.id);
    this.paymentResultPageParams.update(current => ({ ...current, page: 0 }));
    this.loadPaymentResults();
  }

  onPaymentResultPageChange(event: PageEvent): void {
    this.paymentResultPageParams.update(current => ({
      ...current, page: event.pageIndex, size: event.pageSize,
    }));
    this.loadPaymentResults();
  }

  onRunPageChange(event: PageEvent): void {
    this.runPageParams.update(current => ({ ...current, page: event.pageIndex, size: event.pageSize }));
    this.loadRuns();
  }

  displayDate(value?: string): string {
    return value ? new Date(value).toLocaleString() : 'Never';
  }

  webhookUrl(): string {
    return `${window.location.origin}${this.status()?.webhookPath ?? '/api/webhooks/zeffy'}`;
  }

  copyWebhookUrl(): void {
    navigator.clipboard.writeText(this.webhookUrl()).then(
      () => this.notifications.success('Webhook URL copied.'),
      () => this.notifications.error('Could not copy the webhook URL.'),
    );
  }

  latestRunLabel(): string {
    const run = this.status()?.latestCampaignSync;
    return run ? `${run.status} - ${this.displayDate(run.startedAt)}` : 'Never';
  }

  latestCounts(): string {
    const run = this.status()?.latestCampaignSync;
    return run ? this.runCounts(run) : 'No synchronization yet';
  }

  runCounts(run: ZeffySyncRun): string {
    if (run.syncType === 'PAYMENTS') return this.paymentCounts(run);
    return `${run.fetchedCount} fetched, ${run.insertedCount} inserted, ${run.updatedCount} updated, `
      + `${run.ignoredCount} unchanged/ignored, ${run.failedCount} failed`;
  }

  paymentRunLabel(run?: ZeffySyncRun): string {
    return run ? `${run.executionMode ?? ''} ${run.status} - ${this.displayDate(run.startedAt)}`.trim() : 'Never';
  }

  paymentCounts(run?: ZeffySyncRun): string {
    if (!run) return 'No synchronization yet';
    return `${run.fetchedCount} fetched, ${run.eligibleCount} eligible, ${run.processedCount} processed, `
      + `${run.alreadyAppliedCount} already applied, ${run.ignoredCount} ignored, `
      + `${run.needsMappingCount} need mapping, ${run.needsReviewCount} need review, ${run.failedCount} failed`;
  }

  paymentAmount(item: ZeffyPaymentSyncItem): string {
    if (item.amount === undefined || item.amount === null) return '-';
    return new Intl.NumberFormat(undefined, {
      style: 'currency', currency: item.currency ?? 'USD',
    }).format(item.amount);
  }

  private loadStatus(): void {
    this.service.getStatus().subscribe(status => {
      this.status.set(status);
      this.modeDraft = status.integrationMode;
    });
  }

  private loadRuns(): void {
    this.loadingRuns.set(true);
    this.service.getSyncRuns(this.runPageParams()).subscribe({
      next: runs => { this.runs.set(runs); this.loadingRuns.set(false); },
      error: () => this.loadingRuns.set(false),
    });
  }

  private paymentSyncCompleted(run: ZeffySyncRun, action: string): void {
    this.paymentSyncing.set(false);
    this.selectedPaymentRunId.set(run.id);
    this.paymentResultPageParams.update(current => ({ ...current, page: 0 }));
    this.notifications.success(`${action} ${run.status.toLowerCase()}.`);
    this.loadStatus();
    this.loadRuns();
    this.loadPaymentResults();
  }

  private paymentSyncFailed(): void {
    this.paymentSyncing.set(false);
    this.loadStatus();
    this.loadRuns();
  }

  private loadPaymentResults(): void {
    const runId = this.selectedPaymentRunId();
    if (!runId) return;
    this.loadingPaymentResults.set(true);
    this.service.getPaymentSyncResults(runId, this.paymentResultPageParams()).subscribe({
      next: results => { this.paymentResults.set(results); this.loadingPaymentResults.set(false); },
      error: () => this.loadingPaymentResults.set(false),
    });
  }
}
