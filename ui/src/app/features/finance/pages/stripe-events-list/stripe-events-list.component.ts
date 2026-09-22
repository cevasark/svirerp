import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';

import { StripeIntegrationService } from '../../services/stripe-integration.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { StripeWebhookEvent } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';

const STATUS_LABELS: Record<string, string> = {
  received: 'Received',
  processed: 'Processed',
  needs_mapping: 'Needs Mapping',
  error: 'Error',
  ignored: 'Ignored',
};

/** Audit log of every Stripe webhook delivery — mirrors the Zeffy import's row-level audit trail.
 *  'Needs Mapping' rows appear the first time a new Stripe Price is paid for before an admin has
 *  mapped it; 'Reprocess' re-runs the same apply path once the mapping (or whatever else failed)
 *  has been fixed. */
@Component({
  selector: 'app-stripe-events-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Stripe Payments"
        subtitle="Every Stripe webhook received — payments post automatically once their Price is mapped" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No Stripe payments received yet."
        (pageChange)="onPageChange($event)" />
    </div>
  `,
})
export class StripeEventsListComponent implements OnInit {
  private stripeIntegrationService = inject(StripeIntegrationService);
  private orgContext = inject(OrgContextService);
  private notifications = inject(NotificationService);

  private orgId: string | null = null;
  page = signal<Page<StripeWebhookEvent> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>({ ...DEFAULT_PAGE_PARAMS, sort: 'receivedAt,desc' });

  readonly columns: TableColumn[] = [
    { key: 'receivedAt', header: 'Received', cell: (e: StripeWebhookEvent) => e.receivedAt ? new Date(e.receivedAt).toLocaleString() : '—', type: 'date' },
    { key: 'eventType', header: 'Event', cell: (e: StripeWebhookEvent) => e.eventType },
    { key: 'payer', header: 'Payer', cell: (e: StripeWebhookEvent) => e.person ? `${e.person.firstName} ${e.person.lastName}` : (e.email || '—') },
    { key: 'amount', header: 'Amount', cell: (e: StripeWebhookEvent) => e.amount != null ? e.amount.toFixed(2) : '—', type: 'number' },
    { key: 'fee', header: 'Fee', cell: (e: StripeWebhookEvent) => e.fee != null ? e.fee.toFixed(2) : '—', type: 'number' },
    { key: 'status', header: 'Status', cell: (e: StripeWebhookEvent) => STATUS_LABELS[e.status] ?? e.status, type: 'status' },
    { key: 'detail', header: 'Detail', cell: (e: StripeWebhookEvent) => this.detailFor(e) },
  ];

  readonly actions: TableAction[] = [
    {
      icon: 'replay',
      label: 'Reprocess',
      disabled: (e: StripeWebhookEvent) => e.status === 'processed' || e.status === 'ignored',
      action: (e: StripeWebhookEvent) => this.reprocess(e),
    },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  detailFor(event: StripeWebhookEvent): string {
    if (event.status === 'error') return event.errorMessage || 'Unknown error';
    if (event.status === 'needs_mapping') return `Price ${event.stripePriceId ?? '(none)'} isn't mapped yet`;
    if (event.status === 'processed') {
      return event.fee != null ? `Posted to Finance (fee $${event.fee.toFixed(2)})` : 'Posted to Finance';
    }
    if (event.status === 'ignored') return event.errorMessage || 'Not actionable';
    return '—';
  }

  reprocess(event: StripeWebhookEvent): void {
    this.stripeIntegrationService.reprocessEvent(event.id).subscribe({
      next: updated => {
        if (updated.status === 'processed') {
          this.notifications.success('Event reprocessed and posted.');
        } else {
          this.notifications.error(`Still not processed: ${this.detailFor(updated)}`);
        }
        this.loadPage();
      },
    });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.stripeIntegrationService.getEventsForOrg(orgId, this.pageParams()).subscribe({
          next: data => { this.page.set(data); this.loading.set(false); },
          error: () => this.loading.set(false),
        });
      },
      error: () => {
        this.loading.set(false);
        this.notifications.error('No organization found — create one first, under Organizations.');
      },
    });
  }
}
