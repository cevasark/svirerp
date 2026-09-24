import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { DEFAULT_PAGE_PARAMS, Page, PageParams, ZeffyWebhookEventFilters } from '../../../../core/models/api.model';
import { ZeffyWebhookEvent } from '../../../../core/models/domain.model';
import { NotificationService } from '../../../../core/services/notification.service';
import { DataTableComponent, TableAction, TableColumn } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ZeffyIntegrationService } from '../../services/zeffy-integration.service';
import { ZeffyPaymentLifecycleDialogComponent } from './zeffy-payment-lifecycle-dialog.component';

const STATUS_LABELS: Record<string, string> = {
  RECEIVED: 'Received',
  PROCESSING: 'Processing',
  PROCESSED: 'Processed',
  NEEDS_MAPPING: 'Waiting for campaign mapping',
  NEEDS_REVIEW: 'Needs review',
  ERROR: 'Error',
  IGNORED: 'Ignored',
  UNSUPPORTED: 'Unsupported event',
};

@Component({
  selector: 'app-zeffy-webhook-event-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    PageHeaderComponent,
    DataTableComponent,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Zeffy Webhook Events"
        subtitle="Signed Zeffy deliveries, payment lifecycle changes, and processing outcomes">
        <button extraActions mat-stroked-button
          [disabled]="applyingCorrections()" (click)="confirmApplyPendingCorrections()">
          <mat-icon>rule</mat-icon>
          Apply Pending Corrections
        </button>
      </app-page-header>

      <div class="filters">
        <mat-form-field appearance="outline">
          <mat-label>Event type</mat-label>
          <mat-select [(ngModel)]="eventTypeDraft">
            <mat-option value="">All event types</mat-option>
            @for (type of eventTypes; track type) {
              <mat-option [value]="type">{{ type }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusDraft">
            <mat-option value="">All statuses</mat-option>
            @for (status of statuses; track status) {
              <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Payment or contact ID</mat-label>
          <input matInput [(ngModel)]="resourceIdDraft" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Received from</mat-label>
          <input matInput type="datetime-local" [(ngModel)]="receivedFromDraft" />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Received through</mat-label>
          <input matInput type="datetime-local" [(ngModel)]="receivedToDraft" />
        </mat-form-field>

        <div class="filter-actions">
          <button mat-flat-button color="primary" (click)="applyFilters()">Apply</button>
          <button mat-stroked-button (click)="clearFilters()">Clear</button>
        </div>
      </div>

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No Zeffy webhook events match these filters."
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)" />
    </div>
  `,
  styles: [`
    .filters {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
      gap: 12px;
      align-items: start;
      margin-bottom: 12px;
    }
    .filter-actions { display: flex; gap: 8px; min-height: 56px; align-items: center; }
  `],
})
export class ZeffyWebhookEventListComponent implements OnInit {
  private readonly service = inject(ZeffyIntegrationService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly eventTypes = [
    'payment.completed', 'payment.created', 'payment.updated', 'payment.deleted',
    'contact.created', 'contact.updated', 'contact.deleted',
  ];
  readonly statuses = ['RECEIVED', 'NEEDS_REVIEW', 'UNSUPPORTED', 'PROCESSING',
    'PROCESSED', 'NEEDS_MAPPING', 'ERROR', 'IGNORED'];
  readonly pageParams = signal<PageParams>({ ...DEFAULT_PAGE_PARAMS, sort: 'receivedAt,desc' });
  readonly page = signal<Page<ZeffyWebhookEvent> | null>(null);
  readonly filters = signal<ZeffyWebhookEventFilters>({});
  readonly loading = signal(false);
  readonly applyingCorrections = signal(false);

  eventTypeDraft = '';
  statusDraft = '';
  resourceIdDraft = '';
  receivedFromDraft = '';
  receivedToDraft = '';

  readonly columns: TableColumn[] = [
    {
      key: 'receivedAt', header: 'Received', type: 'date', sortable: true,
      cell: (event: ZeffyWebhookEvent) => new Date(event.receivedAt).toLocaleString(),
    },
    { key: 'eventType', header: 'Event', sortable: true },
    {
      key: 'zeffyResourceId', header: 'Payment / Contact ID', type: 'text',
      cell: (event: ZeffyWebhookEvent) => event.zeffyResourceId ?? '-',
    },
    {
      key: 'amount', header: 'Amount', type: 'number',
      cell: (event: ZeffyWebhookEvent) => event.amount == null
        ? '-' : `${event.currency ?? ''} ${event.amount.toFixed(2)}`.trim(),
    },
    {
      key: 'campaignTitle', header: 'Campaign',
      cell: (event: ZeffyWebhookEvent) => event.campaignTitle ?? event.campaignId ?? '-',
    },
    {
      key: 'mapping', header: 'Mapping',
      cell: (event: ZeffyWebhookEvent) => this.mappingDetail(event),
    },
    {
      key: 'status', header: 'Status', type: 'status', sortable: true,
      cell: (event: ZeffyWebhookEvent) => this.statusLabel(event.status),
    },
    { key: 'deliveryCount', header: 'Deliveries', type: 'number', sortable: true },
    {
      key: 'detail', header: 'Detail',
      cell: (event: ZeffyWebhookEvent) => event.errorSummary ?? event.processingSummary ??
        (event.status === 'RECEIVED' ? 'Recorded only' : '-'),
    },
    {
      key: 'records', header: 'Local records',
      cell: (event: ZeffyWebhookEvent) => this.localRecords(event),
    },
  ];

  readonly actions: TableAction[] = [
    {
      icon: 'replay',
      label: 'Reprocess payment event',
      disabled: (event: ZeffyWebhookEvent) => !event.eventType.startsWith('payment.')
        || ['PROCESSED', 'IGNORED', 'UNSUPPORTED', 'PROCESSING'].includes(event.status),
      action: (event: ZeffyWebhookEvent) => this.reprocess(event),
    },
    {
      icon: 'manage_search',
      label: 'View payment lifecycle',
      disabled: (event: ZeffyWebhookEvent) => !event.eventType.startsWith('payment.'),
      action: (event: ZeffyWebhookEvent) => this.viewLifecycle(event),
    },
  ];

  ngOnInit(): void {
    this.load();
  }

  applyFilters(): void {
    this.filters.set({
      eventType: this.eventTypeDraft || undefined,
      status: this.statusDraft || undefined,
      resourceId: this.resourceIdDraft.trim() || undefined,
      receivedFrom: this.toIso(this.receivedFromDraft),
      receivedTo: this.toIso(this.receivedToDraft),
    });
    this.pageParams.update(current => ({ ...current, page: 0 }));
    this.load();
  }

  clearFilters(): void {
    this.eventTypeDraft = '';
    this.statusDraft = '';
    this.resourceIdDraft = '';
    this.receivedFromDraft = '';
    this.receivedToDraft = '';
    this.filters.set({});
    this.pageParams.update(current => ({ ...current, page: 0 }));
    this.load();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.update(current => ({ ...current, page: event.pageIndex, size: event.pageSize }));
    this.load();
  }

  onSortChange(sort: string | null): void {
    this.pageParams.update(current => ({ ...current, page: 0, sort: sort ?? undefined }));
    this.load();
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  mappingDetail(event: ZeffyWebhookEvent): string {
    if (!event.mappingAction) return '-';
    if (event.mappingAction === 'IGNORE') return 'Ignore';
    const membership = event.membershipCredit ? '; membership credit' : '';
    return `${event.mappedFund ?? 'No fund'} / ${event.mappedAccount ?? 'No account'}${membership}`;
  }

  localRecords(event: ZeffyWebhookEvent): string {
    const records = [
      event.personId ? `Person ${event.personId}` : null,
      event.memberId ? `Member ${event.memberId}` : null,
      event.memberPaymentId ? `Contribution ${event.memberPaymentId}` : null,
      event.journalEntryId ? `Journal ${event.journalEntryId}` : null,
    ].filter(Boolean);
    return records.length ? records.join('; ') : '-';
  }

  reprocess(event: ZeffyWebhookEvent): void {
    this.service.reprocessWebhookEvent(event.id).subscribe({
      next: updated => {
        if (updated.status === 'PROCESSED' || updated.status === 'IGNORED') {
          this.notifications.success(`Zeffy event is now ${this.statusLabel(updated.status).toLowerCase()}.`);
        } else {
          this.notifications.error(updated.errorSummary ?? `Event remains ${this.statusLabel(updated.status)}.`);
        }
        this.load();
      },
    });
  }

  viewLifecycle(event: ZeffyWebhookEvent): void {
    this.service.getWebhookEventLifecycle(event.id).subscribe(lifecycle => {
      this.dialog.open(ZeffyPaymentLifecycleDialogComponent, {
        width: '760px',
        data: { event, lifecycle },
      });
    });
  }

  confirmApplyPendingCorrections(): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Apply Pending Zeffy Corrections',
        message: 'Post correction journal entries for pending succeeded refunds, lost disputes, and payment amount changes? Entries use the original payment date and affected memberships will be recomputed.',
        confirmLabel: 'Apply Corrections',
      },
    }).afterClosed().subscribe(confirmed => {
      if (confirmed) this.applyPendingCorrections();
    });
  }

  private applyPendingCorrections(): void {
    this.applyingCorrections.set(true);
    this.service.applyPendingCorrections().subscribe({
      next: result => {
        this.applyingCorrections.set(false);
        const message = `Applied ${result.correctionsApplied} correction(s) across ${result.paymentsScanned} payment(s).`;
        if (result.needsReview || result.failed) {
          this.notifications.info(`${message} ${result.needsReview} need review; ${result.failed} failed.`);
        } else {
          this.notifications.success(message);
        }
        this.load();
      },
      error: () => {
        this.applyingCorrections.set(false);
        this.notifications.error('Could not apply pending Zeffy corrections.');
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.service.getWebhookEvents(this.pageParams(), this.filters()).subscribe({
      next: page => { this.page.set(page); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  private toIso(value: string): string | undefined {
    return value ? new Date(value).toISOString() : undefined;
  }
}
