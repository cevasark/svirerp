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

import { Page, PageParams, ZeffyIntegrationStatus, ZeffySyncRun } from '../../../../core/models/api.model';
import { NotificationService } from '../../../../core/services/notification.service';
import { DataTableComponent, TableColumn } from '../../../../shared/components/data-table/data-table.component';
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
            </mat-select>
          </mat-form-field>
          <p class="hint">
            Record only verifies and stores webhook events without creating people, membership,
            contribution, or accounting records. Live processing becomes available in Phase 3.
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
    mat-progress-spinner { display: inline-block; }
    code { overflow-wrap: anywhere; }
  `],
})
export class ZeffySettingsComponent implements OnInit {
  private readonly service = inject(ZeffyIntegrationService);
  private readonly notifications = inject(NotificationService);

  readonly status = signal<ZeffyIntegrationStatus | null>(null);
  readonly runs = signal<Page<ZeffySyncRun> | null>(null);
  readonly runPageParams = signal<PageParams>({ page: 0, size: 10, sort: 'startedAt,desc' });
  readonly lastConnectionTestAt = signal<string | undefined>(undefined);
  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly syncing = signal(false);
  readonly loadingRuns = signal(false);

  apiKeyDraft = '';
  webhookSecretDraft = '';
  validateApiKey = true;
  modeDraft: 'DISABLED' | 'RECORD_ONLY' = 'DISABLED';

  readonly runColumns: TableColumn[] = [
    { key: 'startedAt', header: 'Started', type: 'date', cell: (r: ZeffySyncRun) => this.displayDate(r.startedAt) },
    { key: 'syncType', header: 'Type', type: 'status' },
    { key: 'status', header: 'Status', type: 'status' },
    { key: 'counts', header: 'Result', cell: (r: ZeffySyncRun) => this.runCounts(r) },
    { key: 'initiatedBy', header: 'Initiated By', cell: (r: ZeffySyncRun) => r.initiatedBy ?? 'System' },
    { key: 'errorSummary', header: 'Error', cell: (r: ZeffySyncRun) => r.errorSummary ?? '-' },
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
        this.modeDraft = status.integrationMode === 'RECORD_ONLY' ? 'RECORD_ONLY' : 'DISABLED';
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
    return `${run.fetchedCount} fetched, ${run.insertedCount} inserted, ${run.updatedCount} updated, `
      + `${run.ignoredCount} unchanged/ignored, ${run.failedCount} failed`;
  }

  private loadStatus(): void {
    this.service.getStatus().subscribe(status => {
      this.status.set(status);
      this.modeDraft = status.integrationMode === 'RECORD_ONLY' ? 'RECORD_ONLY' : 'DISABLED';
    });
  }

  private loadRuns(): void {
    this.loadingRuns.set(true);
    this.service.getSyncRuns(this.runPageParams()).subscribe({
      next: runs => { this.runs.set(runs); this.loadingRuns.set(false); },
      error: () => this.loadingRuns.set(false),
    });
  }
}
